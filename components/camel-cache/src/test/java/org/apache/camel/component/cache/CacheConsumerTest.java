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
import org.apache.camel.component.BaseCacheTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class CacheConsumerTest extends BaseCacheTest {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;
    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testReceivingFileFromCache() throws Exception {
        log.debug("Beginning Test ---> testReceivingFileFromCache()");

        resultEndpoint.expectedMessageCount(3);

        List<String> operations = new ArrayList<String>();
        operations.add(CacheConstants.CACHE_OPERATION_ADD);
        operations.add(CacheConstants.CACHE_OPERATION_UPDATE);
        operations.add(CacheConstants.CACHE_OPERATION_DELETE);
        for (final String operation : operations) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader(CacheConstants.CACHE_OPERATION, operation);
                    in.setHeader(CacheConstants.CACHE_KEY, "greeting");
                    in.setBody("Hello World");
                }
            });
        }

        resultEndpoint.assertIsSatisfied();
        log.debug("Completed Test ---> testReceivingFileFromCache()");
    }

    @Test
    public void testReceivingSerializedObjectFromCache() throws Exception {
        log.debug("Beginning Test ---> testReceivingSerializedObjectFromCache()");

        resultEndpoint.expectedMessageCount(3);

        List<String> operations = new ArrayList<String>();
        operations.add(CacheConstants.CACHE_OPERATION_ADD);
        operations.add(CacheConstants.CACHE_OPERATION_UPDATE);
        operations.add(CacheConstants.CACHE_OPERATION_DELETE);
        for (final String operation : operations) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    Poetry p = new Poetry();
                    p.setPoet("Ralph Waldo Emerson");
                    p.setPoem("Brahma");

                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader(CacheConstants.CACHE_OPERATION, operation);
                    in.setHeader(CacheConstants.CACHE_KEY, "poetry");
                    in.setBody(p);
                }
            });
        }

        resultEndpoint.assertIsSatisfied();
        log.debug("Completed Test ---> testReceivingFileFromCache()");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String operation = (String) exchange.getIn().getHeader(CacheConstants.CACHE_OPERATION);
                        String key = (String) exchange.getIn().getHeader(CacheConstants.CACHE_KEY);
                        Object body = exchange.getIn().getBody();
                        String data = exchange.getContext().getTypeConverter().convertTo(String.class, body);

                        if (log.isDebugEnabled()) {
                            log.debug("------- Cache Event Notification ---------");
                            log.debug("Received notification for the following activity in cache TestCache1:");
                            log.debug("Operation = {}", operation);
                            log.debug("key = {}", key);
                            log.debug("value = {}", data);
                            log.debug("------ End Cache Event Notification ------");
                        }
                    }

                }).to("mock:result");

                from("direct:start").to("cache://TestCache1");
            }
        };
    }

}
