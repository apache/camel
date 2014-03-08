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


import javax.xml.soap.SOAPMessage;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.saaj.SAAJOutInterceptor;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class CxfMessageSoapHeaderOutInterceptor extends AbstractPhaseInterceptor<SoapMessage> {

    public CxfMessageSoapHeaderOutInterceptor() {
        super(Phase.PRE_PROTOCOL);
        addAfter(SAAJOutInterceptor.class.getName());
    }
    public void handleMessage(SoapMessage message) {
        // remove the soap header to avoid the endless loop
        SOAPMessage saaj = message.getContent(SOAPMessage.class);
        if (saaj != null) {
            // AS CXF_MESSAGE already build up all the SOAP message
            // need to clean up the soap Header from message to avoid endless loop
            message.getHeaders().clear();
        }

    }
}