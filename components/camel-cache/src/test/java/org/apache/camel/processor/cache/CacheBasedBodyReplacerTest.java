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
package org.apache.camel.processor.cache;

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
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheBasedBodyReplacerTest extends BaseCacheTest {
    private static final transient Logger LOG = LoggerFactory.getLogger(CacheBasedBodyReplacerTest.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testCacheBasedBodyReplacer() throws Exception {
        LOG.debug("Beginning Test ---> testCacheBasedBodyReplacer()");

        resultEndpoint.expectedMessageCount(1);

        List<String> keys = new ArrayList<String>();
        keys.add("farewell");
        keys.add("greeting");
        for (final String key : keys) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD);
                    in.setHeader(CacheConstants.CACHE_KEY, key);
                    if (key.equalsIgnoreCase("greeting")) {
                        in.setBody("Hello World");
                    } else {
                        in.setBody("Bye World");
                    }
                }
            });
        }

        resultEndpoint.assertIsSatisfied();
        LOG.debug("Completed Test ---> testCacheBasedBodyReplacer()");

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1").filter(header(CacheConstants.CACHE_KEY).isEqualTo("greeting"))
                    .process(new CacheBasedMessageBodyReplacer("cache://TestCache1", "farewell"))
                    .to("direct:next");

                from("direct:next").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String key = (String)exchange.getIn().getHeader(CacheConstants.CACHE_KEY);
                        Object body = exchange.getIn().getBody();
                        String data = exchange.getContext().getTypeConverter().convertTo(String.class, body);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("------- Payload Replacement Results ---------");
                            LOG.debug("The following Payload was replaced from Cache: TestCache1");
                            LOG.debug("key = {}", key);
                            LOG.debug("Before value = Hello World");
                            LOG.debug("After value = {}", data);
                            LOG.debug("------ End  ------");
                        }
                    }
                }).to("mock:result");

                from("direct:start").to("cache://TestCache1");

            }
        };
    }
}
