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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.logging.Log;

/**
 * Exception throw inside Pipeline was not reported or handled when error
 * handler is LoggingErrorHandler. (CAMEL-792)
 */
public class LoggingErrorHandlerTest extends ContextTestSupport {

    private MyLog log = new MyLog();

    public void testLogException() {
        try {
            template.sendBody("direct:in", "Hello World");
        } catch (Exception e) {
            // expected
        }
        assertTrue("Should have logged it", log.logged);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // set to use our logger
                errorHandler(loggingErrorHandler(log));

                from("direct:in").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Hello World");
                    }
                });
            }
        };
    }

    /**
     * Just implement the Log interface, dont wanna mess with easymock or the like at current time
     * for this simple test.
     */
    private class MyLog implements Log {

        boolean logged;

        public boolean isDebugEnabled() {
            return false;
        }

        public boolean isErrorEnabled() {
            return true;
        }

        public boolean isFatalEnabled() {
            return false;
        }

        public boolean isInfoEnabled() {
            return true;
        }

        public boolean isTraceEnabled() {
            return false;
        }

        public boolean isWarnEnabled() {
            return true;
        }

        public void trace(Object message) {
        }

        public void trace(Object message, Throwable t) {
        }

        public void debug(Object message) {
        }

        public void debug(Object message, Throwable t) {
        }

        public void info(Object message) {
        }

        public void info(Object message, Throwable t) {
        }

        public void warn(Object message) {
        }

        public void warn(Object message, Throwable t) {
        }

        public void error(Object message) {
        }

        public void error(Object message, Throwable t) {
            assertNotNull(t);
            assertNotNull(message);
            logged = true;
        }

        public void fatal(Object message) {
        }

        public void fatal(Object message, Throwable t) {
        }
    }


    
}
