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
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class MethodCallBeanRefMethodNotFoundTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    @Test
    public void testMethodCallBeanRefMethodNotFound() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").routeId("a").split().method("foo", "hello").to("mock:a");

                from("direct:b").routeId("b").split().method("foo", "bye").to("mock:b");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            FailedToCreateRouteException failed = assertIsInstanceOf(FailedToCreateRouteException.class, e);
            assertEquals("b", failed.getRouteId());
            MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
            assertEquals("bye", cause.getMethodName());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
