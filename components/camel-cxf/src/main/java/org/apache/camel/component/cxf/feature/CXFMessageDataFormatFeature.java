/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.feature;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;

import org.apache.camel.component.cxf.interceptors.CxfMessageSoapHeaderOutInterceptor;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.soap.SoapBinding;
import org.apache.cxf.binding.soap.saaj.SAAJInInterceptor;
import org.apache.cxf.binding.soap.saaj.SAAJOutInterceptor;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.AbstractInDatabindingInterceptor;
import org.apache.cxf.jaxws.interceptors.HolderInInterceptor;
import org.apache.cxf.jaxws.interceptors.HolderOutInterceptor;
import org.apache.cxf.jaxws.interceptors.MessageModeInInterceptor;
import org.apache.cxf.jaxws.interceptors.MessageModeOutInterceptor;
import org.apache.cxf.jaxws.interceptors.WrapperClassInInterceptor;
import org.apache.cxf.jaxws.interceptors.WrapperClassOutInterceptor;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * MessageDataFormatFeature sets up the CXF endpoint interceptor for handling the
 * Message in Message data format.  
 * </p>
 */
public class CXFMessageDataFormatFeature extends AbstractDataFormatFeature {
    private static final Logger LOG = LoggerFactory.getLogger(CXFMessageDataFormatFeature.class);

    private static final Collection<Class<?>> REMOVING_IN_INTERCEPTORS;
    private static final Collection<Class<?>> REMOVING_OUT_INTERCEPTORS;
   
    static {
        REMOVING_IN_INTERCEPTORS = new ArrayList<>();
        REMOVING_IN_INTERCEPTORS.add(HolderInInterceptor.class);
        REMOVING_IN_INTERCEPTORS.add(WrapperClassInInterceptor.class);
        
        REMOVING_OUT_INTERCEPTORS = new ArrayList<>();
        REMOVING_OUT_INTERCEPTORS.add(HolderOutInterceptor.class);
        REMOVING_OUT_INTERCEPTORS.add(WrapperClassOutInterceptor.class);
    }

    @Override
    public void initialize(Client client, Bus bus) {
        removeFaultInInterceptorFromClient(client);
        setupEndpoint(client.getEndpoint());
    }

    @Override
    public void initialize(Server server, Bus bus) {
        setupEndpoint(server.getEndpoint());
    }
    
    protected void setupEndpoint(Endpoint ep) {
        resetPartTypes(ep.getBinding());

        Class<?> fmt = Source.class;
        if (ep.getBinding() instanceof SoapBinding) {
            ep.getInInterceptors().add(new SAAJInInterceptor());          
            SAAJOutInterceptor out = new SAAJOutInterceptor();
            ep.getOutInterceptors().add(out);
            ep.getOutInterceptors().add(new CxfMessageSoapHeaderOutInterceptor());
            ep.getOutInterceptors().add(new MessageModeOutInterceptor(out, ep.getBinding().getBindingInfo().getName()));
            fmt = SOAPMessage.class;
        } else {
            ep.getOutInterceptors().add(new MessageModeOutInterceptor(Source.class, ep.getBinding().getBindingInfo().getName()));
        }
        ep.getInInterceptors().add(new MessageModeInInterceptor(fmt, ep.getBinding().getBindingInfo().getName()));            
        ep.put(AbstractInDatabindingInterceptor.NO_VALIDATE_PARTS, Boolean.TRUE);
        // need to remove the wrapper class and holder interceptor
        removeInterceptors(ep.getInInterceptors(), REMOVING_IN_INTERCEPTORS);
        removeInterceptors(ep.getOutInterceptors(), REMOVING_OUT_INTERCEPTORS);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
   
    private void resetPartTypes(Binding bop2) {
        // The HypbridSourceDatabinding, based on JAXB, will possibly set
        // JAXB types into the parts.  Since we need the Source objects,
        // we'll reset the types to either Source (for streaming), or null
        // (for non-streaming, defaults to DOMSource.
        for (BindingOperationInfo bop : bop2.getBindingInfo().getOperations()) {
            resetPartTypes(bop);
        }
    }

    private void resetPartTypes(BindingOperationInfo bop) {
        if (bop.isUnwrapped()) {
            bop = bop.getWrappedOperation();
        }
        if (bop.isUnwrappedCapable()) {
            resetPartTypeClass(bop.getWrappedOperation().getOperationInfo().getInput());
            resetPartTypeClass(bop.getWrappedOperation().getOperationInfo().getOutput());
            resetPartTypeClass(bop.getWrappedOperation().getInput());
            resetPartTypeClass(bop.getWrappedOperation().getOutput());
        }
        resetPartTypeClass(bop.getOperationInfo().getInput());
        resetPartTypeClass(bop.getOperationInfo().getOutput());
        resetPartTypeClass(bop.getInput());
        resetPartTypeClass(bop.getOutput());
    }
    
    protected void resetPartTypeClass(BindingMessageInfo bmi) {
        if (bmi != null) {
            int size = bmi.getMessageParts().size();
            for (int x = 0; x < size; x++) {
                bmi.getMessageParts().get(x).setTypeClass(Source.class);
            }
        }
    }
    protected void resetPartTypeClass(MessageInfo msgInfo) {
        if (msgInfo != null) {
            int size = msgInfo.getMessageParts().size();
            for (int x = 0; x < size; x++) {
                msgInfo.getMessageParts().get(x).setTypeClass(Source.class);
            }
        }
    }

}
