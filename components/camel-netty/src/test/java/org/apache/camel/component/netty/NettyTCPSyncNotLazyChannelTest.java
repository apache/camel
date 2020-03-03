/*
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
package org.apache.camel.component.netty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyTCPSyncNotLazyChannelTest extends BaseNettyTest {

    @Test
    public void testTCPStringInOutWithNettyConsumer() throws Exception {
        String response = template.requestBody(
            "netty:tcp://localhost:{{port}}?sync=true&lazyChannelCreation=false",
            "Epitaph in Kohima, India marking the WWII Battle of Kohima and Imphal, Burma Campaign - Attributed to John Maxwell Edmonds", String.class);
        assertEquals("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:tcp://localhost:{{port}}?sync=true")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("When You Go Home, Tell Them Of Us And Say, For Your Tomorrow, We Gave Our Today.");
                        }
                    });
            }
        };
    }

}
