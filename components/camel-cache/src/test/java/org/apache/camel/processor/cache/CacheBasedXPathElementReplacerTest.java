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
import org.junit.Test;

public class CacheBasedXPathElementReplacerTest extends BaseCacheTest {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:loadcache")
    protected ProducerTemplate producerTemplate;

    String xmlFragment = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<books>" + "<book1>" + "</book1>"
         + "<book2>" + "</book2>" + "</books>";

    String book1 = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" 
        + "<book1>" + "<novel>My Man Jeeves</novel>"
        + "<author>P.G Wodehouse</author>" + "</book1>";
    String book2 = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" 
        + "<book2>" + "<novel>The Jungle Book</novel>" 
        + "<author>Rudyard Kipling</author>" + "</book2>";

    @Test
    public void testCacheBasedXPathElementReplacer() throws Exception {
        log.debug("Beginning Test ---> testCacheBasedXPathElementReplacer()");

        resultEndpoint.expectedMessageCount(1);

        List<String> keys = new ArrayList<String>();
        keys.add("book1");
        keys.add("book2");
        keys.add("XML_FRAGMENT");
        for (final String key : keys) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD);
                    in.setHeader(CacheConstants.CACHE_KEY, key);
                    if (key.equalsIgnoreCase("book1")) {
                        in.setBody(book1);
                    } else if (key.equalsIgnoreCase("book2")) {
                        in.setBody(book2);
                    } else {
                        in.setBody(xmlFragment);
                    }
                }
            });
        }

        resultEndpoint.assertIsSatisfied();
        log.debug("Completed Test ---> testCacheBasedXPathElementReplacer()");

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1").filter(header(CacheConstants.CACHE_KEY).isEqualTo("XML_FRAGMENT"))
                    .process(new CacheBasedXPathReplacer("cache://TestCache1", "book1", "/books/book1"))
                    .process(new CacheBasedXPathReplacer("cache://TestCache1", "book2", "/books/book2"))
                    .to("direct:next");

                from("direct:next").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String key = (String)exchange.getIn().getHeader(CacheConstants.CACHE_KEY);
                        Object body = exchange.getIn().getBody();
                        String data = exchange.getContext().getTypeConverter().convertTo(String.class, body);

                        if (log.isDebugEnabled()) {
                            log.debug("------- Payload Replacement Results ---------");
                            log.debug("The following Payload was replaced from Cache: TestCache1");
                            log.debug("key = {}", key);
                            log.debug("Before Value = {}", xmlFragment);
                            log.debug("After value = {}", data);
                            log.debug("------ End  ------");
                        }
                    }
                }).to("mock:result");

                from("direct:loadcache").to("cache://TestCache1");

            }
        };
    }
}
