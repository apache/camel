/**
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
package org.apache.camel.component.cxf.interceptors;

import org.apache.cxf.binding.soap.interceptor.SoapHeaderInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;

/**
 * This interceptor traverses the {@link BindingOperationInfo} and  
 * invokes the {@link MessagePartInfo#setTypeQName(javax.xml.namespace.QName)} method to set
 * the service class to null.  The reason we may want to set the service class to null is 
 * because CXF will try to use JAXB if the service class is present.  It affects DomSource
 * payload to be processed correctly.
 *  
 * @version @Revision: 789534 $
 */
public class RemoveClassTypeInterceptor extends AbstractPhaseInterceptor<Message> {

    public RemoveClassTypeInterceptor() {
        super(Phase.UNMARSHAL);
        addBefore(SoapHeaderInterceptor.class.getName());
    }

    public void handleMessage(Message message) throws Fault {
        Exchange exchange = message.getExchange();
        BindingOperationInfo bop = exchange.getBindingOperationInfo();
        
        if (bop == null) {
            return;    
        }
        
        if (bop.isUnwrapped()) {
            bop = bop.getWrappedOperation();
        }

        if (bop.isUnwrappedCapable()) {
            removePartTypeClass(bop.getWrappedOperation().getOperationInfo().getInput());
            removePartTypeClass(bop.getWrappedOperation().getOperationInfo().getOutput());
            removePartTypeClass(bop.getWrappedOperation().getInput());
            removePartTypeClass(bop.getWrappedOperation().getOutput());
        } else {
            removePartTypeClass(bop.getOperationInfo().getInput());
            removePartTypeClass(bop.getOperationInfo().getOutput());
            removePartTypeClass(bop.getInput());
            removePartTypeClass(bop.getOutput());
        }
    }

    protected void removePartTypeClass(BindingMessageInfo bmi) {
        if (bmi != null) {
            for (MessagePartInfo part : bmi.getMessageParts()) {
                part.setTypeClass(null);
            }     
        }
    }

    protected void removePartTypeClass(MessageInfo msgInfo) {
        if (msgInfo != null) {
            for (MessagePartInfo part : msgInfo.getMessageParts()) {
                part.setTypeClass(null);
            }     
        }
    }

}
