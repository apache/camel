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
package org.apache.camel.processor.onexception;

import java.io.IOException;

import javax.xml.soap.SOAPException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class OnExceptionMisconfiguredTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOnExceptionMisconfigured() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class);

                from("direct:start").to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("OnException[[java.lang.Exception] -> []] is not configured.", iae.getMessage());
        }
    }

    @Test
    public void testOnExceptionMisconfigured2() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).end();

                from("direct:start").to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("OnException[[java.lang.Exception] -> []] is not configured.", iae.getMessage());
        }
    }

    @Test
    public void testOnExceptionMisconfigured3() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                onException();

                from("direct:start").to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("OnException[[java.lang.Exception] -> []] is not configured.", iae.getMessage());
        }
    }

    @Test
    public void testOnExceptionMisconfigured4() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                onException().end();

                from("direct:start").to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("OnException[[java.lang.Exception] -> []] is not configured.", iae.getMessage());
        }
    }

    @Test
    public void testOnExceptionMisconfigured5() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {

                from("direct:start").onException().end().to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith("At least one exception must be configured"));
        }
    }

    @Test
    public void testOnExceptionNotMisconfigured() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                onException().handled(true);

                from("direct:start").to("mock:result");
            }
        });
        context.start();
        // okay
    }

    @Test
    public void testOnExceptionNotMisconfigured2() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                onException().continued(true);

                from("direct:start").to("mock:result");
            }
        });
        context.start();
        // okay
    }

    @Test
    public void testOnExceptionNotMisconfigured3() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true);

                from("direct:start").to("mock:result");
            }
        });
        context.start();
        // okay
    }

    @Test
    public void testOnExceptionNotMisconfigured4() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).continued(true);

                from("direct:start").to("mock:result");
            }
        });
        context.start();
        // okay
    }

    @Test
    public void testOnExceptionNotMisconfigured5() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").onException(SOAPException.class).onException(IOException.class).to("mock:error").end().to("mock:result");
            }
        });
        context.start();
        // okay
    }

}
