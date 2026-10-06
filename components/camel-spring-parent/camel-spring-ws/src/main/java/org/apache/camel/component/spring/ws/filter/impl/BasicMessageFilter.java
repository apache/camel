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
package org.apache.camel.component.spring.ws.filter.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.activation.DataHandler;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.spring.ws.SpringWebserviceConstants;
import org.apache.camel.component.spring.ws.SpringWebserviceHeaderFilterStrategy;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;

/**
 * This class populates a SOAP header and attachments in the WebServiceMessage instance.
 * <p>
 * A message header is written into the SOAP header only when the {@link HeaderFilterStrategy} does not filter it, so by
 * default the internal {@code Camel} and {@code camel} header namespace is not written.
 */
public class BasicMessageFilter implements MessageFilter {

    private final Supplier<HeaderFilterStrategy> headerFilterStrategy;

    /**
     * Creates a filter that applies a {@link SpringWebserviceHeaderFilterStrategy} to the message headers it writes
     * into the SOAP header.
     */
    public BasicMessageFilter() {
        HeaderFilterStrategy strategy = new SpringWebserviceHeaderFilterStrategy();
        this.headerFilterStrategy = () -> strategy;
    }

    /**
     * Creates a filter that applies the {@link HeaderFilterStrategy} returned by the given supplier to the message
     * headers it writes into the SOAP header. A {@code null} strategy disables the filtering.
     *
     * @param headerFilterStrategy supplies the strategy to apply, such as the one configured on the endpoint
     */
    public BasicMessageFilter(Supplier<HeaderFilterStrategy> headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    /**
     * The {@link HeaderFilterStrategy} applied to the message headers written into the SOAP header, or {@code null}
     * when they are not filtered.
     */
    protected HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy != null ? headerFilterStrategy.get() : null;
    }

    /**
     * Whether a header is valid
     */
    protected boolean validHeaderName(String name) {
        return !"Content-Type".equalsIgnoreCase(name);
    }

    @Override
    public void filterProducer(Exchange exchange, WebServiceMessage response) {
        if (exchange != null) {
            processHeaderAndAttachments(exchange.getIn(AttachmentMessage.class), response);
        }
    }

    @Override
    public void filterConsumer(Exchange exchange, WebServiceMessage response) {
        if (exchange != null) {
            AttachmentMessage responseMessage = exchange.getMessage(AttachmentMessage.class);
            processHeaderAndAttachments(responseMessage, response);
        }
    }

    /**
     * If applicable this method adds a SOAP headers and attachments.
     */
    protected void processHeaderAndAttachments(AttachmentMessage inOrOut, WebServiceMessage response) {

        if (response instanceof SoapMessage soapMessage) {
            processSoapHeader(inOrOut, soapMessage);
            doProcessSoapAttachments(inOrOut, soapMessage);
        }
    }

    /**
     * If applicable this method adds a SOAP header.
     */
    protected void processSoapHeader(AttachmentMessage inOrOut, SoapMessage soapMessage) {
        boolean isHeaderAvailable = inOrOut != null && inOrOut.getHeaders() != null && !inOrOut.getHeaders().isEmpty();

        if (isHeaderAvailable) {
            doProcessSoapHeader(inOrOut, soapMessage);
        }
    }

    /**
     * The SOAP header is populated from exchange.getOut().getHeaders() if this class is used by the consumer or
     * exchange.getIn().getHeaders() if this class is used by the producer. If .getHeaders() contains under a certain
     * key a value with the QName object, it is directly added as a new header element. If it contains only a String
     * value, it is transformed into a header attribute. The spring-ws headers, the breadcrumb id and Content-Type are
     * excluded, and so is any header that the {@link HeaderFilterStrategy} filters.
     */
    protected void doProcessSoapHeader(AttachmentMessage inOrOut, SoapMessage soapMessage) {
        SoapHeader soapHeader = soapMessage.getSoapHeader();
        HeaderFilterStrategy strategy = getHeaderFilterStrategy();
        Exchange exchange = inOrOut.getExchange();

        Map<String, Object> headers = inOrOut.getHeaders();

        HashSet<String> headerKeySet = new HashSet<>(headers.keySet());

        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION);
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI);
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION);
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_PRODUCER_FAULT_TO);
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_PRODUCER_REPLY_TO);
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_CONSUMER_FAULT_ACTION);
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_CONSUMER_OUTPUT_ACTION);
        // This gets repeated in the below 'for loop' and gets added as attribute to soapenv:header.
        // This would have already been processed in SpringWebserviceProducer/Consumer instance.
        headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_SOAP_HEADER);

        // Replaced local constant 'BreadcrumbId' with the actual constant key in header 'breadcrumbId'
        // from org.apache.camel.SpringWebserviceConstants.BREADCRUMB_ID. Because of this case mismatch, this key never
        // gets removed from header rather gets added to soapHeader all the time.
        headerKeySet.remove(SpringWebserviceConstants.BREADCRUMB_ID);

        for (String name : headerKeySet) {
            if (!validHeaderName(name)) {
                continue;
            }

            Object value = headers.get(name);
            if (strategy != null && strategy.applyFilterToCamelHeaders(name, value, exchange)) {
                continue;
            }
            if (value instanceof QName qname) {
                soapHeader.addHeaderElement(qname);
            } else {
                if (value instanceof String) {
                    soapHeader.addAttribute(new QName(name), value + "");
                }
            }
        }
    }

    /**
     * Populate SOAP attachments from in or out an exchange message. This the convenient method for overriding.
     */
    protected void doProcessSoapAttachments(AttachmentMessage inOrOut, SoapMessage response) {
        if (inOrOut.hasAttachments()) {
            Map<String, DataHandler> attachments = inOrOut.getAttachments();

            Set<String> keySet = new HashSet<>(attachments.keySet());
            for (String key : keySet) {
                response.addAttachment(key, attachments.get(key));
            }
        }
    }

}
