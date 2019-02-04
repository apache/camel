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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class BeanOverloadedMethodTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testHelloOverloadedHeString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello(String)")
                    .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus");

        template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloOverloadedWildcard() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello(*)")
                    .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus");

        template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloOverloadedStringString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e2
                from("direct:start")
                    .bean(MyBean.class, "hello(String,String)")
                    .to("mock:result");
                // END SNIPPET: e2
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus you are from Denmark");

        template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloOverloadedWildcardString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello(*,String)")
                    .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus you are from Denmark");

        template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloOverloadedWildcardWildcard() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e3
                from("direct:start")
                    .bean(MyBean.class, "hello(*,*)")
                    .to("mock:result");
                // END SNIPPET: e3
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus you are from Denmark");

        template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloOverloadedPickCamelAnnotated() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello")
                    .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus you are from Denmark");

        template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloOverloadedAmbiguousStringStringString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello(String,String,String)")
                    .to("mock:result");

            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            AmbiguousMethodCallException cause = assertIsInstanceOf(AmbiguousMethodCallException.class, e.getCause());
            assertEquals(2, cause.getMethods().size());
        }
    }

    @Test
    public void testHelloOverloadedStringInt() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello(String,int)")
                    .to("mock:result");

            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            AmbiguousMethodCallException cause = assertIsInstanceOf(AmbiguousMethodCallException.class, e.getCause());
            assertEquals(2, cause.getMethods().size());
        }
    }

    @Test
    public void testHelloOverloadedIntString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "hello(int,String)")
                    .to("mock:result");

            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Claus", "country", "Denmark");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            AmbiguousMethodCallException cause = assertIsInstanceOf(AmbiguousMethodCallException.class, e.getCause());
            assertEquals(2, cause.getMethods().size());
        }
    }

    @Test
    public void testTimesOverloadedStringInt() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "times(String,int)")
                    .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("AAA");

        template.sendBodyAndHeader("direct:start", "A", "times", "3");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTimesOverloadedBytesInt() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .bean(MyBean.class, "times(byte[],int)")
                    .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("ABC,ABC,ABC");

        template.sendBodyAndHeader("direct:start", "ABC".getBytes(), "times", "3");

        assertMockEndpointsSatisfied();
    }


    // START SNIPPET: e1
    public static final class MyBean {

        public String hello(String name) {
            return "Hello " + name;
        }

        public String hello(String name, @Header("country") String country) {
            return "Hello " + name + " you are from " + country;
        }

        public String times(String name, @Header("times") int times) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(name);
            }
            return sb.toString();
        }

        public String times(byte[] data, @Header("times") int times) {
            String s = new String(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(s);
                if (i < times - 1) {
                    sb.append(",");
                }
            }
            return sb.toString();
        }

        public String times(String name, int times, char separator) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(name);
                if (i < times - 1) {
                    sb.append(separator);
                }
            }
            return sb.toString();
        }

    }
    // END SNIPPET: e1

}
