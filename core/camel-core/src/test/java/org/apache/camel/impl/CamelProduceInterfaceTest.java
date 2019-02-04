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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.ProxyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class CamelProduceInterfaceTest extends ContextTestSupport {

    private Echo echo;

    @Test
    public void testCamelProduceInterface() throws Exception {
        String reply = echo.hello("Camel");
        assertEquals("Hello Camel", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we gotta cheat and use proxy builder as ContextTestSupport doesnt do
                // all the IoC wiring we need when using @Produce on an interface
                echo = new ProxyBuilder(context).endpoint("direct:hello").build(Echo.class);

                from("direct:hello").transform(body().prepend("Hello "));
            }
        };
    }
}
