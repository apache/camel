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
package org.apache.camel.component.spring.ws.testfilters;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;

/**
 * Adds SOAP header attributes and elements to the response of a spring-ws consumer, the way an invoked web service may
 * append to or rewrite the SOAP header of its response.
 */
public class ResponseSoapHeaderMessageFilter implements MessageFilter {

    private static final String NAMESPACE = "http://example.com/test";

    @Override
    public void filterProducer(Exchange exchange, WebServiceMessage produceResponse) {
        // Do nothing
    }

    @Override
    public void filterConsumer(Exchange exchange, WebServiceMessage consumerResponse) {
        SoapHeader soapHeader = ((SoapMessage) consumerResponse).getSoapHeader();
        // regular application-level header attribute and element
        soapHeader.addAttribute(new QName(NAMESPACE, "MessageId"), "1234567890");
        soapHeader.addHeaderElement(new QName(NAMESPACE, "TransactionId")).setText("42");
        // header attributes and element whose local names are in the internal Camel header namespace
        soapHeader.addAttribute(new QName(NAMESPACE, "CamelHttpUri"), "http://localhost/other");
        soapHeader.addAttribute(new QName(NAMESPACE, "cAmElHttpQuery"), "param=value");
        soapHeader.addHeaderElement(new QName(NAMESPACE, "CamelFileName")).setText("response.xml");
    }
}
