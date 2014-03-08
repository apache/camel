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
package org.apache.camel.component.spring.ws.processor;

import java.io.InputStream;
import java.net.URI;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.spring.ws.SpringWebserviceConstants;

/**
 * Generates static fault response
 */
public class PrecedenceProcessor implements Processor {

    public void process(Exchange exchange) throws Exception {
        // same sample data
        InputStream is = getClass().getResourceAsStream("/stockquote-response.xml");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);

        exchange.getIn().setHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION, new URI("http://actionPrecedence.com"));
        exchange.getIn().setHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_PRODUCER_REPLY_TO, new URI("http://replyPrecedence.to"));
        exchange.getIn().setHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_PRODUCER_FAULT_TO, new URI("http://faultPrecedence.to"));

        exchange.getIn().setHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_CONSUMER_OUTPUT_ACTION, new URI("http://outputHeader.com"));
        exchange.getIn().setHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_CONSUMER_FAULT_ACTION, new URI("http://faultHeader.com"));

        exchange.getOut().copyFrom(exchange.getIn());
        exchange.getOut().setBody(doc);
    }

}
