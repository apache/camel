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
package org.apache.camel.component.spring.ws;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates static response on StockQuote webservice requests
 */
public class StockQuoteResponseProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(StockQuoteResponseProcessor.class);

    public void process(Exchange exchange) throws Exception {
        LOG.info("Crafting standard response in StockQuoteResponseProcessor");
        InputStream is = getClass().getResourceAsStream("/stockquote-response.xml");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        if (exchange.getIn().getHeader("setin") != null && exchange.getIn().getHeader("setin").equals("true")) {
            exchange.getIn().setBody(doc);
        } else {
            exchange.getOut().setBody(doc);
        }
    }

}
