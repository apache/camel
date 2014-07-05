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
package org.apache.camel.component.log;

import java.io.StringWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Custom Logger test.
 */
public class LogCustomLoggerTest extends ContextTestSupport {

    // to capture the logs
    private static StringWriter sw1;
    // to capture the warnings from LogComponent
    private static StringWriter sw2;

    private static final class CapturingAppender extends AppenderSkeleton {
        private StringWriter sw;

        private CapturingAppender(StringWriter sw) {
            this.sw = sw;
        }

        @Override
        protected void append(LoggingEvent event) {
            this.sw.append(event.getLoggerName());
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }

    @Before @Override
    public void setUp() throws Exception {
        super.setUp();
        sw1 = new StringWriter();
        sw2 = new StringWriter();
        Logger.getLogger(LogCustomLoggerTest.class).removeAllAppenders();
        Logger.getLogger(LogCustomLoggerTest.class).addAppender(new CapturingAppender(sw1));
        Logger.getLogger(LogCustomLoggerTest.class).setLevel(Level.TRACE);
        Logger.getLogger("provided.logger1.name").removeAllAppenders();
        Logger.getLogger("provided.logger1.name").addAppender(new CapturingAppender(sw1));
        Logger.getLogger("provided.logger1.name").setLevel(Level.TRACE);
        Logger.getLogger("provided.logger2.name").removeAllAppenders();
        Logger.getLogger("provided.logger2.name").addAppender(new CapturingAppender(sw1));
        Logger.getLogger("provided.logger2.name").setLevel(Level.TRACE);
        Logger.getLogger("irrelevant.logger.name").removeAllAppenders();
        Logger.getLogger("irrelevant.logger.name").addAppender(new CapturingAppender(sw1));
        Logger.getLogger("irrelevant.logger.name").setLevel(Level.TRACE);
        Logger.getLogger(LogComponent.class).removeAllAppenders();
        Logger.getLogger(LogComponent.class).addAppender(new CapturingAppender(sw2));
        Logger.getLogger(LogComponent.class).setLevel(Level.TRACE);
    }

    @Test
    public void testFallbackLogger() throws Exception {
        String endpointUri = "log:" + LogCustomLoggerTest.class.getCanonicalName();
        template.requestBody(endpointUri, "hello");
        assertThat(sw1.toString(), equalTo(LogCustomLoggerTest.class.getCanonicalName()));
    }

    @Test
    public void testEndpointURIParametrizedLogger() throws Exception {
        getRegistry().put("logger1", LoggerFactory.getLogger("provided.logger1.name"));
        getRegistry().put("logger2", LoggerFactory.getLogger("provided.logger2.name"));
        template.requestBody("log:irrelevant.logger.name?logger=#logger2", "hello");
        assertThat(sw1.toString(), equalTo("provided.logger2.name"));
    }

    @Test
    public void testEndpointURIParametrizedNotResolvableLogger() {
        getRegistry().put("logger1", LoggerFactory.getLogger("provided.logger1.name"));
        try {
            template.requestBody("log:irrelevant.logger.name?logger=#logger2", "hello");
        } catch (ResolveEndpointFailedException e) {
            // expected
        }
    }

    @Test
    public void testDefaultRegistryLogger() throws Exception {
        getRegistry().put("logger", LoggerFactory.getLogger("provided.logger1.name"));
        template.requestBody("log:irrelevant.logger.name", "hello");
        assertThat(sw1.toString(), equalTo("provided.logger1.name"));
    }

    @Test
    public void testTwoRegistryLoggers() throws Exception {
        getRegistry().put("logger1", LoggerFactory.getLogger("provided.logger1.name"));
        getRegistry().put("logger2", LoggerFactory.getLogger("provided.logger2.name"));
        template.requestBody("log:irrelevant.logger.name", "hello");
        assertThat(sw1.toString(), equalTo("irrelevant.logger.name"));
        assertThat(sw2.toString(), equalTo(LogComponent.class.getName()));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext(new SimpleRegistry());
    }

    private SimpleRegistry getRegistry() {
        SimpleRegistry registry = null;
        if (context.getRegistry() instanceof PropertyPlaceholderDelegateRegistry) {
            registry = (SimpleRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        } else {
            fail("Could not determine Registry type");
        }
        return registry;
    }

}
