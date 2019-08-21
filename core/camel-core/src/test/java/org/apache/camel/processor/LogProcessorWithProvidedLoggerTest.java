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

import java.io.StringWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.logging.log4j.Level;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.equalTo;

public class LogProcessorWithProvidedLoggerTest extends ContextTestSupport {

    // to capture the logs
    private static StringWriter sw;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        sw = new StringWriter();

        ConsumingAppender.newAppender("org.apache.camel.customlogger", "customlogger", Level.TRACE,
        event -> sw.append(event.getLoggerName() + " " + event.getLevel().toString() + " " + event.getMessage().getFormattedMessage()));
    }

    @Test
    public void testLogProcessorWithRegistryLogger() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        template.sendBody("direct:foo", "Bye World");

        assertMockEndpointsSatisfied();

        assertThat(sw.toString(), equalTo("org.apache.camel.customlogger INFO Got Bye World"));
    }

    @Test
    public void testLogProcessorWithProvidedLogger() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:bar", "Bye World");

        assertMockEndpointsSatisfied();

        assertThat(sw.toString(), equalTo("org.apache.camel.customlogger INFO Also got Bye World"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("mylogger1", LoggerFactory.getLogger("org.apache.camel.customlogger"));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").log(LoggingLevel.INFO, "Got ${body}").to("mock:foo");
                from("direct:bar").routeId("bar").log(LoggingLevel.INFO, LoggerFactory.getLogger("org.apache.camel.customlogger"), "Also got ${body}").to("mock:bar");
            }
        };
    }

}
