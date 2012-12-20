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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class NettyProducerPoolDisabledTest extends BaseNettyTest {

    @Test
    public void testProducerPoolDisabled() throws Exception {
        for (int i = 0; i < 10; i++) {
            String reply = template.requestBody("direct:start", "Hello " + i, String.class);
            assertEquals("Bye " + i, reply);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("netty:tcp://localhost:{{port}}?textline=true&sync=true&producerPoolEnabled=false");

                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                    // body should be a String when using textline codec
                    .validate(body().isInstanceOf(String.class))
                    .transform(body().regexReplaceAll("Hello", "Bye"));
            }
        };
    }
}