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
package org.apache.camel.itest.security;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import static org.junit.Assert.assertTrue;

public class CXFMessageProcessor implements Processor {
    static final String RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soap:Body><greetMeResponse xmlns=\"http://apache.org/hello_world_soap_http/types\">"
        + "<responseType> Hello CXF</responseType></greetMeResponse></soap:Body></soap:Envelope>";

    public void process(Exchange exchange) throws Exception {
        // just print out the request message
        Message in = exchange.getIn();
        String request = in.getBody(String.class);
        // just make sure the request is greetme
        assertTrue("It should be GreetMe request.", request.indexOf("<greetMe") > 0);
        InputStream is = new ByteArrayInputStream(RESPONSE.getBytes());
        SOAPMessage message = MessageFactory.newInstance().createMessage(null, is);
        exchange.getOut().setBody(message);
    }
}
