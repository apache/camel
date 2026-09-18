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
package org.apache.camel.component.exec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.component.exec.impl.DefaultExecBinding;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultExecBindingTest extends CamelTestSupport {

    @Test
    void shouldReadArgsFromHeaderWhenControlHeadersEnabled() throws Exception {
        List<String> args = Arrays.asList("arg1", "arg2");
        ExecCommand command = readInput("exec:test", args, true);
        assertEquals(args, command.getArgs());

        command = readInput("exec:test", "arg1 arg2", true);
        assertEquals(args, command.getArgs());

        command = readInput("exec:test?args=arg1 arg2", null, true);
        assertEquals(args, command.getArgs());

        command = readInput("exec:test", Collections.emptyList(), true);
        assertEquals(Collections.emptyList(), command.getArgs());
    }

    @Test
    void shouldIgnoreControlHeadersByDefault() throws Exception {
        DefaultExecBinding binding = new DefaultExecBinding();
        ExecEndpoint execEndpoint = createExecEndpoint("exec:hostname", false);
        Exchange exchange = execEndpoint.createExchange();
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE, "whoami");
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_ARGS, "ARGS-WORK");

        ExecCommand command = binding.readInput(exchange, execEndpoint);

        assertEquals("hostname", command.getExecutable());
        assertEquals(Collections.emptyList(), command.getArgs());
        assertEquals("whoami", exchange.getIn().getHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE));
        assertEquals("ARGS-WORK", exchange.getIn().getHeader(ExecBinding.EXEC_COMMAND_ARGS));
    }

    @Test
    void shouldApplyControlHeadersWhenEnabled() throws Exception {
        DefaultExecBinding binding = new DefaultExecBinding();
        ExecEndpoint execEndpoint = createExecEndpoint("exec:hostname", true);
        Exchange exchange = execEndpoint.createExchange();
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE, "whoami");
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_ARGS, "ARGS-WORK");

        ExecCommand command = binding.readInput(exchange, execEndpoint);

        assertEquals("whoami", command.getExecutable());
        assertEquals(List.of("ARGS-WORK"), command.getArgs());
        assertNull(exchange.getIn().getHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE));
        assertNull(exchange.getIn().getHeader(ExecBinding.EXEC_COMMAND_ARGS));
    }

    @Test
    void shouldKeepUriArgsWhenControlHeadersDisabled() throws Exception {
        ExecCommand command = readInput("exec:echo?args=URIARGS-WORK", "ARGS-WORK", false);

        assertEquals(List.of("URIARGS-WORK"), command.getArgs());
        assertEquals("echo", command.getExecutable());
    }

    @Test
    void shouldWarnOncePerEndpointWhenControlHeadersIgnored() throws Exception {
        List<String> warnings = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("CaptureWarn", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN
                        && event.getMessage().getFormattedMessage().contains("Control header")) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(DefaultExecBinding.class);
        logger.addAppender(appender);

        try {
            DefaultExecBinding binding = new DefaultExecBinding();
            ExecComponent component = context.getComponent("exec", ExecComponent.class);
            component.setAllowControlHeaders(false);
            component.setBinding(binding);

            ExecEndpoint endpoint1 = (ExecEndpoint) component.createEndpoint("exec:hostname");
            ExecEndpoint endpoint2 = (ExecEndpoint) component.createEndpoint("exec:echo");

            Exchange exchange1 = endpoint1.createExchange();
            exchange1.getIn().setHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE, "whoami");
            binding.readInput(exchange1, endpoint1);

            Exchange exchange2 = endpoint2.createExchange();
            exchange2.getIn().setHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE, "whoami");
            binding.readInput(exchange2, endpoint2);

            Exchange exchange3 = endpoint1.createExchange();
            exchange3.getIn().setHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE, "whoami");
            binding.readInput(exchange3, endpoint1);

            assertEquals(2, warnings.size(), "Expected one warning per distinct exec endpoint");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldApplyControlHeadersFromEndpointOption() throws Exception {
        DefaultExecBinding binding = new DefaultExecBinding();
        ExecComponent component = context.getComponent("exec", ExecComponent.class);
        component.setAllowControlHeaders(false);
        ExecEndpoint execEndpoint = (ExecEndpoint) component.createEndpoint("exec:hostname?allowControlHeaders=true");
        Exchange exchange = execEndpoint.createExchange();
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE, "whoami");

        ExecCommand command = binding.readInput(exchange, execEndpoint);

        assertEquals("whoami", command.getExecutable());
        assertNull(exchange.getIn().getHeader(ExecBinding.EXEC_COMMAND_EXECUTABLE));
    }

    private ExecCommand readInput(String execEndpointUri, Object args, boolean allowControlHeaders) throws Exception {
        DefaultExecBinding binding = new DefaultExecBinding();
        ExecEndpoint execEndpoint = createExecEndpoint(execEndpointUri, allowControlHeaders);
        Exchange exchange = execEndpoint.createExchange();
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_ARGS, args);
        return binding.readInput(exchange, execEndpoint);
    }

    private ExecEndpoint createExecEndpoint(String uri, boolean allowControlHeaders) throws Exception {
        ExecComponent component = context.getComponent("exec", ExecComponent.class);
        component.setAllowControlHeaders(allowControlHeaders);
        return (ExecEndpoint) component.createEndpoint(uri);
    }

}
