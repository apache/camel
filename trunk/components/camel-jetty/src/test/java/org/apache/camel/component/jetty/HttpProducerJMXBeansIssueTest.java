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
package org.apache.camel.component.jetty;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class HttpProducerJMXBeansIssueTest extends BaseJettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProducerJMXBeansIssueTest.class);

    @Override
    public void setUp() throws Exception {
        // to enable the JMX connector
        enableJMX();
        System.setProperty("org.apache.camel.jmx.createRmiConnector", "True");
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/leak").transform(constant("Bye World"));

                from("direct:leak").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        LOG.debug("URL is: " + exchange.getIn().getHeader("url"));
                    }
                }).recipientList(header("url"));
            }
        };
    }

    @Test
    public void testNothing() {
        // do nothing as this test is manual
    }

    // @Test
    // TODO: disabled as this is a manual test
    public void testSendAlot() throws Exception {
        Endpoint ep = context.getEndpoint("direct:leak");
        Producer p = ep.createProducer();
        p.start();

        for (int i = 0; i < 10000; i++) {
            Exchange ex = ep.createExchange();
            ex.getIn().setHeader("url", "http://localhost:{{port}}/leak?id=" + i);
            p.process(ex);
        }

        p.stop();
    }

}
