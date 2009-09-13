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

public class CacheBasedBodyReplacerTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CacheBasedBodyReplacerTest.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    
    @Test
    public void testCacheBasedBodyReplacer() throws Exception {
    	LOG.info("Beginning Test ---> testCacheBasedBodyReplacer()");
    	
    	resultEndpoint.expectedMessageCount(1);
        
        List<String> keys = new ArrayList<String>();
        keys.add("farewell");
        keys.add("greeting");
        for (final String key : keys) {
            producerTemplate.send(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                    Message in = exchange.getIn();
                    in.setHeader("CACHE_OPERATION", "ADD");
                    in.setHeader("CACHE_KEY", key);
                    if (key.equalsIgnoreCase("greeting")) {
                        in.setBody("Hello World");
                    } else {
                        in.setBody("Bye World");
                    }
                }
            });
        }
        
        resultEndpoint.assertIsSatisfied();
    	LOG.info("Completed Test ---> testCacheBasedBodyReplacer()");
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1").
                    filter(header("CACHE_KEY").isEqualTo("greeting")).
                    process (new CacheBasedMessageBodyReplacer("cache://TestCache1","farewell")).
                    to("direct:next");
                
                from("direct:next").
                    process (new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String key = (String) exchange.getIn().getHeader("CACHE_KEY");
                            Object body = exchange.getIn().getBody();
                            String data = exchange.getContext().getTypeConverter().convertTo(String.class, body);                        
                                
                            LOG.info("------- Payload Replacement Results ---------");
                            LOG.info("The following Payload was replaced from Cache: TestCache1");
                            LOG.info("key = " + key);
                            LOG.info("Before value = Hello World");
                            LOG.info("After value = " + data);
                            LOG.info("------ End  ------");   
                        }
                    }).
                    to("mock:result");                 
                
                from("direct:start").
                    to("cache://TestCache1");

            }
        };
    }
}
