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
package org.apache.camel.component.plc4x;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class Plc4XComponentTagTest extends CamelTestSupport {

    @Test
    public void testSimpleRouting() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedMessageCount(2);
        template.sendBody("direct:plc4x", Collections.singletonList("irrelevant"));
        template.sendBody("direct:plc4x2", Collections.singletonList("irrelevant"));

        MockEndpoint.assertIsSatisfied(context, 2, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:plc4x")
                        .setBody(constant(Collections.singletonMap("test", Collections.singletonMap("testAddress", false))))
                        .to("plc4x:mock:10.10.10.1/1/1")
                        .to("mock:result");
                from("direct:plc4x2")
                        .setBody(constant(Collections.singletonMap("test2", Collections.singletonMap("testAddress2", 0x05))))
                        .to("plc4x:mock:10.10.10.1/1/1")
                        .to("mock:result");
                from("plc4x:mock:10.10.10.1/1/1?autoReconnect=true&tag.Test1=%TestQuery")
                        .log("Got ${body}");
            }
        };
    }

}
