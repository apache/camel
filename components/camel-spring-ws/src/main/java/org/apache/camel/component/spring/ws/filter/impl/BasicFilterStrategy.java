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
package org.apache.camel.component.spring.ws.filter.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.spring.ws.SpringWebserviceConstants;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;

/**
 * This class populates a SOAP header and attachments
 * 
 * 
 */
public class BasicFilterStrategy implements MessageFilter {
    private static final String BREADCRUMB_ID = "breadcrumbId";

    @Override
    public void filterProducer(Exchange exchange, WebServiceMessage response) {
        if (exchange != null) {
            processHeaderAndAttachemtns(exchange.getIn(), response);
        }
    }

    @Override
    public void filterConsumer(Exchange exchange, WebServiceMessage response) {
        if (exchange != null) {
            processHeaderAndAttachemtns(exchange.getOut(), response);
        }
    }

    /**
     * If applicable this method adds a SOAP header and attachments.
     * 
     * @param inOrOut
     * @param response
     */
    protected void processHeaderAndAttachemtns(Message inOrOut, WebServiceMessage response) {

        if (response instanceof SoapMessage) {
            SoapMessage soapMessage = (SoapMessage)response;
            processHeader(inOrOut, soapMessage);
            processAttachements(inOrOut, soapMessage);
        }
    }

    /**
     * The SOAP header is populated from exchange.getOut().getHeaders() if this
     * class is used by the consumer or exchange.getIn().getHeaders() if this
     * class is used by the producer.
     * 
     * If .getHeaders() contains under a certain key a value with the QName
     * object, it is directly added as a new header element. If it contains only
     * a String value, it is transformed into a header attribute.
     * 
     * Following headers are excluded: {@code BREADCRUMB_ID}
     * 
     * @see SpringWebserviceConstants.SPRING_WS_SOAP_ACTION, @see
     *      SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION), @see
     *      SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI
     * 
     * @param inOrOut
     * @param soapMessage
     */
    protected void processHeader(Message inOrOut, SoapMessage soapMessage) {
        boolean isHeaderAvailable = inOrOut != null && inOrOut.getHeaders() != null
                                    && !inOrOut.getHeaders().isEmpty();

        if (isHeaderAvailable) {
            SoapHeader soapHeader = soapMessage.getSoapHeader();

            Map<String, Object> headers = inOrOut.getHeaders();

            Set<String> headerKeySet = new HashSet<String>(headers.keySet());

            headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION);
            headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION);
            headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI);
            headerKeySet.remove(BREADCRUMB_ID);

            for (String name : headerKeySet) {
                Object value = headers.get(name);

                if (value instanceof QName) {
                    soapHeader.addHeaderElement((QName)value);
                } else {
                    if (value instanceof String) {
                        soapHeader.addAttribute(new QName(name), value + "");
                    }
                }
            }
        }
    }

    /**
     * Populate SOAP attachments from in or out exchange message.
     * 
     * @param inOrOut
     * @param response
     */
    protected void processAttachements(Message inOrOut, SoapMessage response) {
        Map<String, DataHandler> attachments = inOrOut.getAttachments();

        Set<String> keySet = new HashSet<String>(attachments.keySet());
        for (String key : keySet) {
            response.addAttachment(key, attachments.get(key));
        }
    }

}
