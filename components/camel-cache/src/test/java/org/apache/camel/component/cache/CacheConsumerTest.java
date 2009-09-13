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

package org.apache.camel.component.cache;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class CacheConsumerTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CacheConsumerTest.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;
    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testReceivingFileFromCache() throws Exception {
        LOG.info("Beginning Test ---> testReceivingFileFromCache()");

        resultEndpoint.expectedMessageCount(3);

        List<String> operations = new ArrayList<String>();
        operations.add("ADD");
        operations.add("UPDATE");
        operations.add("DELETE");
        for (final String operation : operations) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader("CACHE_OPERATION", operation);
                    in.setHeader("CACHE_KEY", "greeting");
                    in.setBody("Hello World");
                }
            });
        }

        resultEndpoint.assertIsSatisfied();
        LOG.info("Completed Test ---> testReceivingFileFromCache()");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String operation = (String)exchange.getIn().getHeader("CACHE_OPERATION");
                        String key = (String)exchange.getIn().getHeader("CACHE_KEY");
                        Object body = exchange.getIn().getBody();
                        String data = exchange.getContext().getTypeConverter().convertTo(String.class, body);

                        LOG.info("------- Cache Event Notification ---------");
                        LOG.info("Received notification for the following activity in cache TestCache1:");
                        LOG.info("Operation = " + operation);
                        LOG.info("key = " + key);
                        LOG.info("value = " + data);
                        LOG.info("------ End Cache Event Notification ------");
                    }

                }).to("mock:result");

                from("direct:start").to("cache://TestCache1");
            }
        };
    }

}
