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
package org.apache.camel.main;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MainIoCBeanPostProcessorDisabledTest {

    @Test
    public void testMainIoCEnabled() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("tony");

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        main.stop();
    }

    @Test
    public void testMainIoCDisabled() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.configure().withBeanPostProcessorEnabled(false);

        try {
            main.start();
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            NoSuchBeanException nsbe = (NoSuchBeanException) e.getCause();
            assertEquals("tiger", nsbe.getName());
        }
    }

    public static class MyRouteBuilder extends RouteBuilder {

        @BindToRegistry("tiger")
        private String foo = "tony";

        @Override
        public void configure() throws Exception {
            from("direct:start").bean("tiger", "toString").to("mock:results");
        }
    }
}
