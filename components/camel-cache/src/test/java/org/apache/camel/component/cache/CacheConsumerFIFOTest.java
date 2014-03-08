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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class CacheConsumerFIFOTest extends CacheConsumerTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cache://TestCache1?memoryStoreEvictionPolicy=FIFO").process(new Processor() {
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
