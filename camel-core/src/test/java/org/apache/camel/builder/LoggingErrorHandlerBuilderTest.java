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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class LoggingErrorHandlerBuilderTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testLoggingErrorHandler() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                LoggingErrorHandlerBuilder eh = loggingErrorHandler();
                eh.setLevel(LoggingLevel.ERROR);
                eh.setLog(LoggerFactory.getLogger("foo"));

                assertEquals(LoggingLevel.ERROR, eh.getLevel());
                assertNotNull(eh.getLog());
                assertFalse(eh.supportTransacted());

                errorHandler(eh);

                from("direct:start").to("mock:foo").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }

    public void testLoggingErrorHandler2() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(loggingErrorHandler().level(LoggingLevel.WARN).log(LoggerFactory.getLogger("foo")));

                from("direct:start").to("mock:foo").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }

    public void testLoggingErrorHandler3() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                LoggingErrorHandlerBuilder eh = loggingErrorHandler(LoggerFactory.getLogger("foo"));
                eh.setLevel(LoggingLevel.ERROR);

                assertEquals(LoggingLevel.ERROR, eh.getLevel());
                assertNotNull(eh.getLog());
                assertFalse(eh.supportTransacted());

                errorHandler(eh);

                from("direct:start").to("mock:foo").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }

    public void testLoggingErrorHandler4() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                LoggingErrorHandlerBuilder eh = loggingErrorHandler(LoggerFactory.getLogger("foo"), LoggingLevel.ERROR);

                assertEquals(LoggingLevel.ERROR, eh.getLevel());
                assertNotNull(eh.getLog());
                assertFalse(eh.supportTransacted());

                errorHandler(eh);

                from("direct:start").to("mock:foo").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }

    public void testLoggingErrorHandler5() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(loggingErrorHandler().level(LoggingLevel.ERROR).logName("foo"));

                from("direct:start").to("mock:foo").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }

    public void testLoggingErrorHandler6() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(loggingErrorHandler().level(LoggingLevel.WARN).logName("foo"));

                from("direct:start").routeId("myRoute")
                    .to("mock:foo")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }
}
