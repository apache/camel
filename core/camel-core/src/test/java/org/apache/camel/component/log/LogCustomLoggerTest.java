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
package org.apache.camel.component.log;

import java.io.StringWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Custom Logger test.
 */
public class LogCustomLoggerTest extends ContextTestSupport {

    // to capture the logs
    private static StringWriter sw1;
    // to capture the warnings from LogComponent
    private static StringWriter sw2;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        sw1 = new StringWriter();
        sw2 = new StringWriter();

        ConsumingAppender.newAppender(LogCustomLoggerTest.class.getCanonicalName(), "LogCustomLoggerTest", Level.TRACE, event -> sw1.append(event.getLoggerName()));
        ConsumingAppender.newAppender("provided.logger1.name", "logger1", Level.TRACE, event -> sw1.append(event.getLoggerName()));
        ConsumingAppender.newAppender("provided.logger2.name", "logger2", Level.TRACE, event -> sw1.append(event.getLoggerName()));
        ConsumingAppender.newAppender("irrelevant.logger.name", "irrelevant", Level.TRACE, event -> sw1.append(event.getLoggerName()));
        ConsumingAppender.newAppender(LogComponent.class.getCanonicalName(), "LogComponent", Level.INFO, event -> sw2.append(event.getLoggerName()));
    }

    @Test
    public void testFallbackLogger() throws Exception {
        String endpointUri = "log:" + LogCustomLoggerTest.class.getCanonicalName();
        template.requestBody(endpointUri, "hello");

        assertThat(sw1.toString(), equalTo(LogCustomLoggerTest.class.getCanonicalName()));
    }

    @Test
    public void testEndpointURIParametrizedLogger() throws Exception {
        context.getRegistry().bind("logger1", LoggerFactory.getLogger("provided.logger1.name"));
        context.getRegistry().bind("logger2", LoggerFactory.getLogger("provided.logger2.name"));
        template.requestBody("log:irrelevant.logger.name?logger=#logger2", "hello");
        assertThat(sw1.toString(), equalTo("provided.logger2.name"));
    }

    @Test
    public void testEndpointURIParametrizedNotResolvableLogger() {
        context.getRegistry().bind("logger1", LoggerFactory.getLogger("provided.logger1.name"));
        try {
            template.requestBody("log:irrelevant.logger.name?logger=#logger2", "hello");
        } catch (ResolveEndpointFailedException e) {
            // expected
        }
    }

    @Test
    public void testDefaultRegistryLogger() throws Exception {
        context.getRegistry().bind("logger", LoggerFactory.getLogger("provided.logger1.name"));
        template.requestBody("log:irrelevant.logger.name", "hello");
        assertThat(sw1.toString(), equalTo("provided.logger1.name"));
    }

    @Test
    public void testTwoRegistryLoggers() throws Exception {
        context.getRegistry().bind("logger1", LoggerFactory.getLogger("provided.logger1.name"));
        context.getRegistry().bind("logger2", LoggerFactory.getLogger("provided.logger2.name"));
        template.requestBody("log:irrelevant.logger.name", "hello");
        assertThat(sw1.toString(), equalTo("irrelevant.logger.name"));
        assertThat(sw2.toString(), equalTo(LogComponent.class.getName()));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

}
