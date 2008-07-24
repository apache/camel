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
package org.apache.camel.component.log;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;

/**
 * Log formatter test.
 */
public class LogFormatterTest extends ContextTestSupport {

    public void testSendMessageToLogDefault() throws Exception {
        template.sendBody("log:org.apache.camel.TEST", "Hello World");
    }

    public void testSendMessageToLogSingleOptions() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showExchangeId=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showProperties=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showHeaders=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showBodyType=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showBody=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showOut=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showAll=true", "Hello World");
    }

    public void testSendMessageToLogMultiOptions() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showHeaders=true&showOut=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showProperties=true&showHeaders=true&showOut=true", "Hello World");
    }

    public void testSendMessageToLogShowFalse() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showBodyType=false", "Hello World");
    }

    public void testSendMessageToLogMultiLine() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?multiline=true", "Hello World");
    }

    public void testSendByteArrayMessageToLogDefault() throws Exception {
        template.sendBody("log:org.apache.camel.TEST", "Hello World".getBytes());
    }

    public void testSendExchangeWithOut() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showAll=true&multiline=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getOut().setBody(22);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

}
