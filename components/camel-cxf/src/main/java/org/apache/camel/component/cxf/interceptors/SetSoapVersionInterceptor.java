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
package org.apache.camel.component.cxf.interceptors;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;

public class SetSoapVersionInterceptor extends AbstractSoapInterceptor {

    public SetSoapVersionInterceptor() {
        super(Phase.WRITE);
        addBefore(SoapOutInterceptor.class.getName());
    }
    @Override
    public void handleMessage(SoapMessage message) throws Fault {
        if (message.getExchange() != null) { 
            if (message.getExchange().getInMessage() instanceof SoapMessage) {
                message.setVersion(((SoapMessage)message.getExchange().getInMessage()).getVersion());
            } 
        }
    }

}
