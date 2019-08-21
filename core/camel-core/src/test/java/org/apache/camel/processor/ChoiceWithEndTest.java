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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ChoiceWithEndTest extends ContextTestSupport {

    @Test
    public void testRouteIsCorrectAtRuntime() throws Exception {
        // use navigate to find that the end works as expected
        Navigate<Processor> nav = getRoute("direct://start").navigate();
        List<Processor> node = nav.next();

        // there should be 4 outputs as the end in the otherwise should
        // ensure that the transform and last send is not within the choice
        assertEquals(4, node.size());
        // the navigate API is a bit simple at this time of writing so it does
        // take a little
        // bit of ugly code to find the correct processor in the runtime route
        assertIsInstanceOf(SendProcessor.class, unwrapChannel(node.get(0)).getNextProcessor());
        assertIsInstanceOf(ChoiceProcessor.class, unwrapChannel(node.get(1)).getNextProcessor());
        assertIsInstanceOf(TransformProcessor.class, unwrapChannel(node.get(2)).getNextProcessor());
        assertIsInstanceOf(SendProcessor.class, unwrapChannel(node.get(3)).getNextProcessor());
    }

    private Route getRoute(String routeEndpointURI) {
        Route answer = null;
        for (Route route : context.getRoutes()) {
            if (routeEndpointURI.equals(route.getEndpoint().getEndpointUri())) {
                answer = route;
                break;
            }
        }
        return answer;
    }

    @Test
    public void testChoiceHello() throws Exception {
        getMockEndpoint("mock:start").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:echo").expectedBodiesReceived("echo Hello World");
        getMockEndpoint("mock:last").expectedBodiesReceived("last echo Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testChoiceBye() throws Exception {
        getMockEndpoint("mock:start").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:bye").expectedBodiesReceived("We do not care");
        getMockEndpoint("mock:last").expectedBodiesReceived("last We do not care");

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testChoiceOther() throws Exception {
        getMockEndpoint("mock:start").expectedBodiesReceived("Camel");
        getMockEndpoint("mock:other").expectedBodiesReceived("other Camel");
        getMockEndpoint("mock:last").expectedBodiesReceived("last other Camel");

        template.sendBody("direct:start", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyChoiceBean bean = new MyChoiceBean();

                from("direct:start").to("mock:start").choice().when(body().contains("Hello")).bean(bean, "echo").to("mock:echo").when(body().contains("Bye"))
                    // must use another route as the Java DSL
                    // will lose its scope so you cannot call otherwise later
                    .to("direct:bye").to("mock:bye").otherwise().bean(bean, "other").to("mock:other").end().transform(body().prepend("last ")).to("mock:last");

                from("direct:bye").doTry().bean(bean, "bye").to("mock:bye").doCatch(Exception.class).setBody(constant("We do not care")).end();
            }
        };
    }

    public class MyChoiceBean {

        public String echo(String s) {
            return "echo " + s;
        }

        public String bye(String s) throws Exception {
            throw new IllegalArgumentException("Damn does not work");
        }

        public String other(String s) {
            return "other " + s;
        }
    }

}
