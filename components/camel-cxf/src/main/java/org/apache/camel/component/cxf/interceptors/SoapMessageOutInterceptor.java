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
        // header is not store as the element
        Element header = message.get(Element.class);

        List<Element> payload = message.get(List.class);
        Exchange exchange = message.getExchange();
        BindingMessageInfo bmi = exchange.get(BindingMessageInfo.class);


        //Headers -represent as -Element,Body -represent as StaxStream.
        //Check if BindingOperationInfo contains header
        List<SoapHeaderInfo> bindingHdr = bmi.getExtensors(SoapHeaderInfo.class);
        if (bindingHdr != null && !bindingHdr.isEmpty()) {
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("SoapMessageOutInterceptor BindingOperation header processing.");
            }

            List<Element> headerList = new ArrayList<Element>();
            List<Element> newPayload = new ArrayList<Element>(payload);
            //Look for headers in Payload.
            for (SoapHeaderInfo shi : bindingHdr) {
                List<Element> tmpList = new ArrayList<Element>();
                MessagePartInfo mpi = shi.getPart();
                QName hdrName = mpi.getConcreteName();
                for (Element el : payload) {
                    QName elName = new QName(el.getNamespaceURI(), el.getLocalName());
                    if (elName.equals(hdrName)) {
                        newPayload.remove(el);
                        tmpList.add(el);
                    }
                }

                if (tmpList.size() > 1) {
                    throw new Fault(new org.apache.cxf.common.i18n.Message(
                                    "MULTIPLE_HDR_PARTS", LOG, hdrName));
                }
                headerList.addAll(tmpList);
            }

            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("DOMOutInterceptor Copy Payload parts to SOAPHeaders");
            }
            if (headerList.size() != 0) {
                SoapVersion version = ((SoapMessage)message).getVersion();
                header = createElement(version.getHeader(), headerList);
            }
            payload = newPayload;
        }

        //Set SOAP Header Element.
        //Child Elements Could be binding specified parts or user specified headers.
        //REVISTED the soap headers
        //message.setHeaders(Element.class, header);

        //TODO Moving Parts from Header to Payload.
        //For e.g Payload ROuting from SOAP11 <-> SOAP12

        //So write payload and header to outbound message
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
