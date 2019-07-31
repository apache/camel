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

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.component.cxf.interceptors.ConfigureDocLitWrapperInterceptor;
import org.apache.camel.component.cxf.interceptors.SetSoapVersionInterceptor;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.soap.SoapBinding;
import org.apache.cxf.binding.soap.interceptor.SoapHeaderInterceptor;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.interceptors.HolderInInterceptor;
import org.apache.cxf.jaxws.interceptors.HolderOutInterceptor;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This feature just setting up the CXF endpoint interceptor for handling the
 * Message in PAYLOAD data format
 */
public class PayLoadDataFormatFeature extends AbstractDataFormatFeature {
    private static final Logger LOG = LoggerFactory.getLogger(PayLoadDataFormatFeature.class);
    private static final boolean DEFAULT_ALLOW_STREAMING;
    static {
        
        String s = System.getProperty("org.apache.camel.component.cxf.streaming");
        DEFAULT_ALLOW_STREAMING = s == null || Boolean.parseBoolean(s);
    }

    boolean allowStreaming = DEFAULT_ALLOW_STREAMING;
    
    public PayLoadDataFormatFeature() {
    }
    public PayLoadDataFormatFeature(Boolean streaming) {
        if (streaming != null) {
            allowStreaming = streaming;
        }
    }
    
    
    @Override
    public void initialize(Client client, Bus bus) {
        client.getEndpoint().put("org.apache.cxf.binding.soap.addNamespaceContext", "true");
        removeFaultInInterceptorFromClient(client);
        
        // Need to remove some interceptors that are incompatible
        // We don't support JAX-WS Holders for PAYLOAD (not needed anyway)
        // and thus we need to remove those interceptors to prevent Holder
        // object from being created and stuck into the contents list
        // instead of Source objects
        removeInterceptor(client.getEndpoint().getInInterceptors(), 
                          HolderInInterceptor.class);
        removeInterceptor(client.getEndpoint().getOutInterceptors(), 
                          HolderOutInterceptor.class);
        // The SoapHeaderInterceptor maps various headers onto method parameters.
        // At this point, we expect all the headers to remain as headers, not
        // part of the body, so we remove that one.
        removeInterceptor(client.getEndpoint().getBinding().getInInterceptors(), 
                          SoapHeaderInterceptor.class);
        client.getEndpoint().getBinding().getInInterceptors().add(new ConfigureDocLitWrapperInterceptor(true));
        resetPartTypes(client.getEndpoint().getBinding());

        LOG.info("Initialized CXF Client: {} in Payload mode with allow streaming: {}", client, allowStreaming);
    }


    @Override
    public void initialize(Server server, Bus bus) {
        server.getEndpoint().put("org.apache.cxf.binding.soap.addNamespaceContext", "true");
        server.getEndpoint().getBinding().getInInterceptors().add(new ConfigureDocLitWrapperInterceptor(true));
        if (server.getEndpoint().getBinding() instanceof SoapBinding) {
            server.getEndpoint().getBinding().getOutInterceptors().add(new SetSoapVersionInterceptor());
        }
        // Need to remove some interceptors that are incompatible
        // See above.
        removeInterceptor(server.getEndpoint().getInInterceptors(), 
                          HolderInInterceptor.class);
        removeInterceptor(server.getEndpoint().getOutInterceptors(), 
                          HolderOutInterceptor.class);
        removeInterceptor(server.getEndpoint().getBinding().getInInterceptors(), 
                          SoapHeaderInterceptor.class);
        resetPartTypes(server.getEndpoint().getBinding());

        LOG.info("Initialized CXF Server: {} in Payload mode with allow streaming: {}", server, allowStreaming);
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
        } else {
            resetPartTypeClass(bop.getOperationInfo().getInput());
            resetPartTypeClass(bop.getOperationInfo().getOutput());
            resetPartTypeClass(bop.getInput());
            resetPartTypeClass(bop.getOutput());
        }
    }
    
    protected void resetPartTypeClass(BindingMessageInfo bmi) {
        if (bmi != null) {
            int size = bmi.getMessageParts().size();
            for (int x = 0; x < size; x++) {
                //last part can be streamed, others need DOM parsing 
                if (x < (size - 1)) {
                    bmi.getMessageParts().get(x).setTypeClass(allowStreaming ? DOMSource.class : null);
                } else {
                    bmi.getMessageParts().get(x).setTypeClass(allowStreaming ? Source.class : null);
                }
            }
        }
    }
    protected void resetPartTypeClass(MessageInfo msgInfo) {
        if (msgInfo != null) {
            int size = msgInfo.getMessageParts().size();
            for (int x = 0; x < size; x++) {
                //last part can be streamed, others need DOM parsing 
                if (x < (size - 1)) {
                    msgInfo.getMessageParts().get(x).setTypeClass(allowStreaming ? DOMSource.class : null);
                } else {
                    msgInfo.getMessageParts().get(x).setTypeClass(allowStreaming ? Source.class : null);
                }
            }
        }
    }
    
}
