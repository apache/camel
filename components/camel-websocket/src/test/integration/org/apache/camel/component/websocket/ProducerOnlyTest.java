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
package org.apache.camel.component.websocket;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ProducerOnlyTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WebsocketComponentLiveTest.class);

    @Test
    public void liveTest() throws Exception {
        LOG.info("*** open URL  http://localhost:1989/producer-only.html ***");
        Thread.sleep(1 * 60 * 1000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {

            private Counter counter = new Counter();

            public void configure() {

                WebsocketComponent component = getContext().getComponent("websocket", WebsocketComponent.class);
                component.setHost("localhost");
                component.setPort(1989);
                component.setStaticResources("src/test/resources");

                from("timer://foo?fixedRate=true&period=1000").bean(counter).setHeader(WebsocketConstants.SEND_TO_ALL, constant(true)).to("websocket://counter");
            }
        };
    }

    public class Counter {

        //private int counter = 0;

        private int counter;
        
        public int next() {
            return ++counter;
        }
    }
}
