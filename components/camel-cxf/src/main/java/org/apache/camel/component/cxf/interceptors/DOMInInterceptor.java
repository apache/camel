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

import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.CheckFaultInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapActionInInterceptor;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.XMLMessage;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class DOMInInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LogUtils.getL7dLogger(DOMOutInterceptor.class);
    private final XMLMessageInInterceptor xmlInterceptor = new XMLMessageInInterceptor();
    private final SoapMessageInInterceptor soapInterceptor = new SoapMessageInInterceptor();
    public DOMInInterceptor() {
        super(Phase.POST_PROTOCOL);
        this.addAfter(CheckFaultInterceptor.class.getName());
    }

    public boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }

    public void handleMessage(Message message) throws Fault {
        if (message instanceof XMLMessage) {
            xmlInterceptor.handleMessage((XMLMessage)message);
        } else if (message instanceof SoapMessage) {
            soapInterceptor.handleMessage((SoapMessage)message);
        } else {
            throw new Fault(new org.apache.cxf.common.i18n.Message("NOT_SUPPORTED_MESSAGE",
                                                                   LOG, message.getClass().getName()));
        }
    }
}
