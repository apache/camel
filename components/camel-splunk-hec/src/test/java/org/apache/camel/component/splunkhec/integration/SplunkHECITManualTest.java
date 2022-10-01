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
package org.apache.camel.component.splunkhec.integration;

import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.splunkhec.SplunkHECComponent;
import org.apache.camel.component.splunkhec.SplunkHECConfiguration;
import org.apache.camel.component.splunkhec.SplunkHECEndpoint;
import org.apache.camel.component.splunkhec.SplunkHECProducer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("run manually since it requires a running local splunk server")
public class SplunkHECITManualTest extends CamelTestSupport {

    @Test
    public void testSendHEC() throws Exception {
        CamelContext ctx = new DefaultCamelContext();

        SplunkHECConfiguration configuration = new SplunkHECConfiguration();
        configuration.setSkipTlsVerify(true);
        configuration.setIndex("camel");
        configuration.setSource("camel");
        configuration.setSourceType("camel");
        SplunkHECComponent component = new SplunkHECComponent();
        SplunkHECEndpoint endpoint = new SplunkHECEndpoint(
                "splunk-hec:localhost:8088/4b35e71f-6a0f-4bab-94ce-f591ff45eecd", component, configuration);
        assertEquals("4b35e71f-6a0f-4bab-94ce-f591ff45eecd", endpoint.getToken());
        endpoint.setCamelContext(ctx);
        SplunkHECProducer producer = new SplunkHECProducer(endpoint);
        producer.start();

        Exchange ex = new DefaultExchange(ctx);
        DefaultMessage message = new DefaultMessage(ex);
        message.setBody("TEST sending to Splunk");
        message.setHeader("foo", "bar");
        ex.setIn(message);
        producer.process(ex);

        Exchange ex2 = new DefaultExchange(ctx);
        DefaultMessage message2 = new DefaultMessage(ex2);
        message2.setBody(Collections.singletonMap("key", "value"));
        ex2.setIn(message2);
        producer.process(ex2);

        Exchange ex3 = new DefaultExchange(ctx);
        DefaultMessage message3 = new DefaultMessage(ex3);
        message3.setBody(null);
        ex3.setIn(message3);
        producer.process(ex3);

        producer.stop();
    }

    @Test
    public void testCamelRoute() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:hec-result");
        mock.expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("direct:hec", "My splunk data", "header", "headerValue");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:hec").to(
                        "splunk-hec:localhost:8088/4b35e71f-6a0f-4bab-94ce-f591ff45eecd?source=camelsource&skipTlsVerify=true")
                        .to("mock:hec-result");
            }
        };
    }
}
