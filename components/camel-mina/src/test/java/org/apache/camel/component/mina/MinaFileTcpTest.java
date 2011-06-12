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
package org.apache.camel.component.mina;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class MinaFileTcpTest extends BaseMinaTest {

    @Test
    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMessageCount(1);
        endpoint.message(0).body().startsWith("Hello World");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // lets setup a server
                from("mina:tcp://localhost:{{port}}?sync=false&textline=true")
                    .to("mock:results");

                from("file:src/test/data?noop=true&fileName=message1.txt").
                    to("mina:tcp://localhost:{{port}}?sync=false&textline=true");
            }
        };
    }
}