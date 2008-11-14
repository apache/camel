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

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.wsdl.Definition;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.model.SoapBindingInfo;
import org.apache.cxf.binding.soap.model.SoapHeaderInfo;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.wsdl11.WSDLServiceBuilder;

public class SoapMessageOutInterceptor extends AbstractMessageOutInterceptor<SoapMessage> {
    private static final Logger LOG = LogUtils.getL7dLogger(SoapMessageInInterceptor.class);


    public SoapMessageOutInterceptor() {
        super(Phase.PREPARE_SEND);
        addAfter(DOMOutInterceptor.class.getName());
    }

    protected Logger getLogger() {
        return LOG;
    }

    @SuppressWarnings("unchecked")
    public void handleMessage(SoapMessage message) throws Fault {        

        List<Element> payload = message.get(List.class);
        Exchange exchange = message.getExchange();
        BindingMessageInfo bmi = exchange.get(BindingMessageInfo.class);
        //The soap header is handled by the SoapOutInterceptor

        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("SoapMessageOutInterceptor binding operation style processing.");
        }
        SoapBindingInfo soapBinding = (SoapBindingInfo)exchange.get(BindingInfo.class);
        String style = soapBinding.getStyle(bmi.getBindingOperation().getOperationInfo());
        if ("rpc".equals(style)) {
            //Add the Operation Node or Operation+"Response" node
            //Remove the operation element.
            OperationInfo oi = bmi.getBindingOperation().getOperationInfo();
            Endpoint ep = exchange.get(Endpoint.class);
            Definition def =
                ep.getService().getServiceInfos().get(0).getProperty(WSDLServiceBuilder.WSDL_DEFINITION,
                                                             Definition.class);
            String prefix = def.getPrefix(oi.getName().getNamespaceURI());

            if ("".equals(prefix)) {
                prefix = "tns";
            }
            QName opName = null;
            boolean isClient = isRequestor(message);
            if (isClient) {
                opName = new QName(oi.getName().getNamespaceURI(),
                                   oi.getName().getLocalPart(),
                                   prefix);
            } else {
                opName = new QName(oi.getName().getNamespaceURI(),
                                   oi.getName().getLocalPart() + "Response",
                                   prefix);
            }
            Element opEl = createElement(opName, payload);
            payload = new ArrayList<Element>();
            payload.add(opEl);
        }

        message.put(List.class, payload);
    }
}
