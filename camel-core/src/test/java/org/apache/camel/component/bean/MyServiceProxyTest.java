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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MyServiceProxyTest extends ContextTestSupport {

    public void testOk() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        String reply = myService.method("Hello World");
        assertEquals("Camel in Action", reply);
    }

    public void testKaboom() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        try {
            myService.method("Kaboom");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Damn", e.getMessage());
        }
    }

    public void testCheckedException() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        try {
            myService.method("Tiger in Action");
            fail("Should have thrown exception");
        } catch (MyApplicationException e) {
            assertEquals("No tigers", e.getMessage());
            assertEquals(9, e.getCode());
        }
    }

    public void testNestedRuntimeCheckedException() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        try {
            myService.method("Donkey in Action");
            fail("Should have thrown exception");
        } catch (MyApplicationException e) {
            assertEquals("No donkeys", e.getMessage());
            assertEquals(8, e.getCode());
        }
    }

    public void testNestedCheckedCheckedException() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:start"), MyService.class);
        try {
            myService.method("Elephant in Action");
            fail("Should have thrown exception");
        } catch (MyApplicationException e) {
            assertEquals("No elephants", e.getMessage());
            assertEquals(7, e.getCode());
        }
    }
    
    public void testRequestAndResponse() throws Exception {
        MyService myService = ProxyHelper.createProxy(context.getEndpoint("direct:request"), MyService.class);
        MyRequest in = new MyRequest();
        in.id = 100;
        in.request = "Camel";
        MyResponse response = myService.call(in);
        assertEquals("Get a wrong response id.", 100, response.id);
        assertEquals("Get a wrong response", "Hi Camel", response.response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when(body().isEqualTo("Tiger in Action")).throwException(new MyApplicationException("No tigers", 9))
                        .when(body().isEqualTo("Donkey in Action")).throwException(new RuntimeCamelException(new MyApplicationException("No donkeys", 8)))
                        .when(body().isEqualTo("Elephant in Action")).throwException(new MyCustomException("Damn", new MyApplicationException("No elephants", 7)))
                        .when(body().isEqualTo("Kaboom")).throwException(new IllegalArgumentException("Damn"))
                        .otherwise().transform(constant("Camel in Action"));
                
                from("direct:request").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        MyRequest request = exchange.getIn().getBody(MyRequest.class);
                        MyResponse response = new MyResponse();
                        response.id = request.id;
                        response.response = "Hi " + request.request;
                        // we need to setup the body as a response
                        exchange.getOut().setBody(response);
                    }

                });
            }
        };
    }

}
