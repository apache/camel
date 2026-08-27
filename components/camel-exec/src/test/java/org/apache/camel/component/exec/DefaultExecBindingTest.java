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

import org.apache.camel.Exchange;
import org.apache.camel.component.exec.impl.DefaultExecBinding;
import org.apache.camel.test.junit6.CamelTestSupport;
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
