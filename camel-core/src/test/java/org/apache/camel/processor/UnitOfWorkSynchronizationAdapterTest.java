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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;

/**
 * @version 
 */
public class UnitOfWorkSynchronizationAdapterTest extends UnitOfWorkTest {

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:async").to("direct:foo");
                from("direct:foo").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        log.info("Received: " + exchange);

                        exchange.getUnitOfWork().addSynchronization(new SynchronizationAdapter() {
                            @Override
                            public void onComplete(Exchange exchange) {
                                completed = exchange;
                                foo = exchange.getIn().getHeader("foo");
                                doneLatch.countDown();
                            }
                        });

                        exchange.getUnitOfWork().addSynchronization(new SynchronizationAdapter() {
                            @Override
                            public void onFailure(Exchange exchange) {
                                failed = exchange;
                                baz = exchange.getIn().getHeader("baz");
                                doneLatch.countDown();
                            }
                        });

                        String name = getName();
                        if (name.equals("testFail")) {
                            log.info("Failing test!");
                            exchange.getOut().setFault(true);
                            exchange.getOut().setBody("testFail() should always fail with a fault!");
                        } else if (name.equals("testException")) {
                            log.info("Throwing exception!");
                            throw new Exception("Failing test!");
                        }
                    }
                });
            }
        };
    }
}