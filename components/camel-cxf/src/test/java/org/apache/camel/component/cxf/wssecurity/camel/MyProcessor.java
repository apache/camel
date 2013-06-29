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
package org.apache.camel.component.cxf.wssecurity.camel;

import java.io.InputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * This processor is used to create a new SOAPMessage from the in message stream
 * which will be used as output message.
 */
public class MyProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // take out the soap message as an inputStream
        InputStream is = exchange.getIn().getBody(InputStream.class);
        // put it as an soap message
        SOAPMessage message = MessageFactory.newInstance().createMessage(null, is);
        exchange.getOut().setBody(message);
    }

}
