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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class MyServiceProxyTest extends ContextTestSupport {

    @Test
    public void testOk() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        String reply = myService.method("Hello World");
        assertEquals("Camel in Action", reply);
    }

    @Test
    public void testKaboom() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        try {
            myService.method("Kaboom");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Damn", e.getMessage());
        }
    }

    @Test
    public void testCheckedException() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);

        MyApplicationException e = assertThrows(MyApplicationException.class,
                () -> myService.method("Tiger in Action"),
                "Should have thrown exception");

        assertEquals("No tigers", e.getMessage());
        assertEquals(9, e.getCode());
    }

    @Test
    public void testNestedRuntimeCheckedException() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);

        MyApplicationException e = assertThrows(MyApplicationException.class,
                () -> myService.method("Donkey in Action"),
                "Should have thrown exception");

        assertEquals("No donkeys", e.getMessage());
        assertEquals(8, e.getCode());
    }

    @Test
    public void testNestedCheckedCheckedException() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);

        MyApplicationException e = assertThrows(MyApplicationException.class,
                () -> myService.method("Elephant in Action"),
                "Should have thrown exception");

        assertEquals("No elephants", e.getMessage());
        assertEquals(7, e.getCode());
    }

    @Test
    public void testRequestAndResponse() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:request"), MyService.class);
        MyRequest in = new MyRequest();
        in.id = 100;
        in.request = "Camel";
        MyResponse response = myService.call(in);
        assertEquals(100, response.id, "Get a wrong response id.");
        assertEquals("Hi Camel", response.response, "Get a wrong response");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when(body().isEqualTo("Tiger in Action"))
                        .throwException(new MyApplicationException("No tigers", 9))
                        .when(body().isEqualTo("Donkey in Action"))
                        .throwException(new RuntimeCamelException(new MyApplicationException("No donkeys", 8)))
                        .when(body().isEqualTo("Elephant in Action"))
                        .throwException(new MyCustomException("Damn", new MyApplicationException("No elephants", 7)))
                        .when(body().isEqualTo("Kaboom")).throwException(new IllegalArgumentException("Damn")).otherwise()
                        .transform(constant("Camel in Action"));

                from("direct:request").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        MyRequest request = exchange.getIn().getBody(MyRequest.class);
                        MyResponse response = new MyResponse();
                        response.id = request.id;
                        response.response = "Hi " + request.request;
                        // we need to setup the body as a response
                        exchange.getMessage().setBody(response);
                    }

                });
            }
        };
    }

}
