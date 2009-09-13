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

import java.io.InputStream;
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

public class CacheBasedTokenReplacerTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CacheBasedTokenReplacerTest.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:loadcache")
    protected ProducerTemplate producerTemplate;

    String quote = "#novel# - #author#\n" + "'Tis all a Chequer-board of Nights and Days\n"
                   + "Where Destiny with Men for Pieces plays:\n"
                   + "Hither and thither moves, and mates, and slays,\n"
                   + "And #number# by #number# back in the Closet lays.";

    @Test
    public void testCacheBasedTokenReplacer() throws Exception {
        LOG.info("Beginning Test ---> testCacheBasedTokenReplacer()");

        resultEndpoint.expectedMessageCount(1);

        List<String> keys = new ArrayList<String>();
        keys.add("novel");
        keys.add("author");
        keys.add("number");
        keys.add("quote");
        for (final String key : keys) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader("CACHE_OPERATION", "ADD");
                    in.setHeader("CACHE_KEY", key);
                    if (key.equalsIgnoreCase("novel")) {
                        in.setBody("Rubaiyat");
                    } else if (key.equalsIgnoreCase("author")) {
                        in.setBody("Omar Khayyam");
                    } else if (key.equalsIgnoreCase("number")) {
                        in.setBody("one");
                    } else {
                        in.setBody(quote);
                    }
                }
            });
        }

        resultEndpoint.assertIsSatisfied();
        LOG.info("Completed Test ---> testCacheBasedTokenReplacer()");

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1").filter(header("CACHE_KEY").isEqualTo("quote"))
                    .process(new CacheBasedTokenReplacer("cache://TestCache1", "novel", "#novel#"))
                    .process(new CacheBasedTokenReplacer("cache://TestCache1", "author", "#author#"))
                    .process(new CacheBasedTokenReplacer("cache://TestCache1", "number", "#number#"))
                    .to("direct:next");

                from("direct:next").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String key = (String)exchange.getIn().getHeader("CACHE_KEY");
                        Object body = exchange.getIn().getBody();
                        String data = exchange.getContext().getTypeConverter().convertTo(String.class, body);

                        LOG.info("------- Payload Replacement Results ---------");
                        LOG.info("The following Payload was replaced from Cache: TestCache1");
                        LOG.info("key = " + key);
                        LOG.info("Before Value = " + quote);
                        LOG.info("After value = " + data);
                        LOG.info("------ End  ------");
                    }
                }).to("mock:result");

                from("direct:loadcache").to("cache://TestCache1");

            }
        };
    }
}
