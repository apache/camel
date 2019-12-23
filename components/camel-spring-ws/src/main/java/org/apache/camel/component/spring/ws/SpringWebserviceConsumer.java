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
package org.apache.camel.component.spring.ws;

import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.xml.namespace.QName;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachmentMessage;
import org.apache.camel.support.DefaultConsumer;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.mime.Attachment;
import org.springframework.ws.mime.MimeMessage;
import org.springframework.ws.server.endpoint.MessageEndpoint;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import static org.apache.camel.component.spring.ws.SpringWebserviceHelper.toResult;

public class SpringWebserviceConsumer extends DefaultConsumer implements MessageEndpoint {

    private SpringWebserviceEndpoint endpoint;
    private SpringWebserviceConfiguration configuration;

    public SpringWebserviceConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = (SpringWebserviceEndpoint)endpoint;
        this.configuration = this.endpoint.getConfiguration();
    }

    /**
     * Invoked by Spring-WS when a {@link WebServiceMessage} is received
     */
    @Override
    public void invoke(MessageContext messageContext) throws Exception {
        Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOptionalOut);
        populateExchangeFromMessageContext(messageContext, exchange);
        
        // populate camel exchange with breadcrumb from transport header        
        populateExchangeWithBreadcrumbFromMessageContext(messageContext, exchange);
        
        // start message processing
        getProcessor().process(exchange);

        if (exchange.getException() != null) {
            throw exchange.getException();
        } else if (exchange.getPattern().isOutCapable()) {
            Message responseMessage = exchange.getMessage(Message.class);
            if (responseMessage != null) {
                Source responseBody = responseMessage.getBody(Source.class);
                WebServiceMessage response = messageContext.getResponse();
                configuration.getMessageFilter().filterConsumer(exchange, response);
                toResult(responseBody, response.getPayloadResult());
            }
        }
    }

    private void populateExchangeWithBreadcrumbFromMessageContext(MessageContext messageContext, Exchange exchange) {
        if (messageContext.getRequest() instanceof SaajSoapMessage) {
            SaajSoapMessage saajSoap = (SaajSoapMessage) messageContext.getRequest();
            populateExchangeWithBreadcrumbFromSaajMessage(exchange, saajSoap);
        } else {
            populateExchangeWithBreadcrumbFromMessageContext(exchange, messageContext);
        }
    }

    private void populateExchangeWithBreadcrumbFromSaajMessage(Exchange exchange, SaajSoapMessage saajSoap) {
        SOAPMessage soapMessageRequest;
        if (saajSoap != null) {
            soapMessageRequest = saajSoap.getSaajMessage();
            if (soapMessageRequest != null) {
                MimeHeaders mimeHeaders = soapMessageRequest.getMimeHeaders();
                if (mimeHeaders != null) {
                    String[] breadcrumbIdHeaderValues = mimeHeaders.getHeader(Exchange.BREADCRUMB_ID);
                    // expected to get one token
                    // if more than one token expected, 
                    // presumably breadcrumb generation strategy 
                    // may be required to implement
                    if (breadcrumbIdHeaderValues != null && breadcrumbIdHeaderValues.length >= 1) {
                        exchange.getIn().setHeader(Exchange.BREADCRUMB_ID, breadcrumbIdHeaderValues[0]);
                    }
                }
            }
        }
    }

    private void populateExchangeWithBreadcrumbFromMessageContext(Exchange exchange, MessageContext messageContext) {
        if (messageContext != null) {
            HttpServletRequest obj = (HttpServletRequest) messageContext.getProperty("transport.http.servletRequest");
            String breadcrumbId = obj.getHeader(Exchange.BREADCRUMB_ID);
            exchange.getIn().setHeader(Exchange.BREADCRUMB_ID, breadcrumbId);
        }
    }

    private void populateExchangeFromMessageContext(MessageContext messageContext, Exchange exchange) {
        populateExchangeWithPropertiesFromMessageContext(messageContext, exchange);

        // create inbound message
        WebServiceMessage request = messageContext.getRequest();
        SpringWebserviceMessage inMessage = new SpringWebserviceMessage(exchange.getContext(), request);
        exchange.setIn(inMessage);
        extractSourceFromSoapHeader(inMessage.getHeaders(), request);
        extractAttachmentsFromRequest(request, new DefaultAttachmentMessage(inMessage));
    }

    private void populateExchangeWithPropertiesFromMessageContext(MessageContext messageContext,
                                                                  Exchange exchange) {
        // convert WebserviceMessage properties (added through interceptors) to
        // Camel exchange properties
        String[] propertyNames = messageContext.getPropertyNames();
        if (propertyNames != null) {
            for (String propertyName : propertyNames) {
                exchange.setProperty(propertyName, messageContext.getProperty(propertyName));
            }
        }
    }

    /**
     * Extracts the SOAP headers and set them as headers in the Exchange. Also sets
     * it as a header with the key SpringWebserviceConstants.SPRING_WS_SOAP_HEADER
     * and a value of type Source.
     *
     * @param headers the Exchange Headers
     * @param request the WebService Request
     */
    private void extractSourceFromSoapHeader(Map<String, Object> headers, WebServiceMessage request) {
        if (request instanceof SoapMessage) {
            SoapMessage soapMessage = (SoapMessage)request;
            SoapHeader soapHeader = soapMessage.getSoapHeader();

            if (soapHeader != null) {
                //Set the raw soap header as a header in the exchange.
                headers.put(SpringWebserviceConstants.SPRING_WS_SOAP_HEADER, soapHeader.getSource());

                //Set header values for the soap header attributes
                Iterator<QName> attIter = soapHeader.getAllAttributes();
                while (attIter.hasNext()) {
                    QName name = attIter.next();
                    headers.put(name.getLocalPart(), soapHeader.getAttributeValue(name));
                }

                //Set header values for the soap header elements
                Iterator<SoapHeaderElement> elementIter = soapHeader.examineAllHeaderElements();
                while (elementIter.hasNext()) {
                    SoapHeaderElement element = elementIter.next();
                    QName name = element.getName();
                    headers.put(name.getLocalPart(), element);

                }
            }
        }
    }

    private void extractAttachmentsFromRequest(final WebServiceMessage request,
                                               final AttachmentMessage inMessage) {
        if (request instanceof MimeMessage) {
            Iterator<Attachment> attachmentsIterator = ((MimeMessage)request).getAttachments();
            while (attachmentsIterator.hasNext()) {
                Attachment attachment = attachmentsIterator.next();
                inMessage.addAttachment(attachment.getContentId(), attachment.getDataHandler());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (configuration.getEndpointMapping() != null) {
            configuration.getEndpointMapping().removeConsumer(configuration.getEndpointMappingKey());
        }
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getEndpointMapping() != null) {
            configuration.getEndpointMapping().addConsumer(configuration.getEndpointMappingKey(), this);
        }
        super.doStart();
    }

}
