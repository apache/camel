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
package org.apache.camel.component.ai.tool;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiToolExecutorTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:greetUser"
                     + "?tags=test"
                     + "&description=Greet a user by name"
                     + "&parameter.name=string"
                     + "&parameter.name.description=The user name"
                     + "&parameter.name.required=true"
                     + "&parameter.age=integer"
                     + "&parameter.age.description=The user age")
                        .setBody(simple("Hello ${header.name}, age ${header.age}"));

                from("ai-tool:noParams"
                     + "?tags=test"
                     + "&description=A tool with no parameters")
                        .setBody(constant("no-param result"));

                from("ai-tool:nullBodyTool"
                     + "?tags=test"
                     + "&description=A tool that returns null body")
                        .setBody(constant((Object) null));

                from("ai-tool:failingTool"
                     + "?tags=test"
                     + "&description=A tool that always fails")
                        .throwException(new RuntimeException("Simulated failure"));

                from("ai-tool:exchangeExceptionTool"
                     + "?tags=test"
                     + "&description=A tool that sets exception on exchange")
                        .process(exchange -> exchange.setException(
                                new RuntimeException("Exchange-level failure")));
            }
        };
    }

    @Test
    public void testExecuteWithDeclaredArguments() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Alice");
        arguments.put("age", 30);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .isEqualTo("Hello Alice, age 30");
    }

    @Test
    public void testExecuteIgnoresUndeclaredArguments() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Bob");
        arguments.put("age", 25);
        arguments.put("extraParam", "should-be-ignored");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("Undeclared arguments should not affect the result")
                .isEqualTo("Hello Bob, age 25");
        assertThat(exchange.getMessage().getHeader("extraParam"))
                .as("Undeclared argument must not be set as header")
                .isNull();
    }

    @Test
    public void testExecuteWithNullArguments() {
        AiToolSpec spec = findSpec("noParams");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .isEqualTo("no-param result");
    }

    @Test
    public void testExecuteWithEmptyArguments() {
        AiToolSpec spec = findSpec("noParams");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .isEqualTo("no-param result");
    }

    @Test
    public void testExecuteReturnsExecutionErrorOnRouteFailure() {
        AiToolSpec spec = findSpec("failingTool");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
        assertThat(error.message()).contains("failingTool").contains("Simulated failure");
        assertThat(error.cause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testExecuteReturnsExecutionErrorOnExchangeException() {
        AiToolSpec spec = findSpec("exchangeExceptionTool");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
        assertThat(error.message()).contains("exchangeExceptionTool").contains("Exchange-level failure");
        assertThat(error.cause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testExecuteReturnsArgumentErrorForMissingRequired() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("age", 30);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ArgumentError.class);
        AiToolResult.ArgumentError error = (AiToolResult.ArgumentError) result;
        assertThat(error.message()).contains("Missing required argument 'name'");
        assertThat(error.cause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testExecuteReturnsExecutionErrorForNullConsumer() {
        AiToolSpec spec = new AiToolSpec("ghostTool", "A tool with no consumer", Map.of(), null, null);
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
        assertThat(error.message()).contains("No consumer available for tool 'ghostTool'");
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testExecuteReturnsExecutionErrorForNullProcessor() {
        AiToolEndpoint endpoint = context.getEndpoint(
                "ai-tool:nullProc?tags=test&description=test", AiToolEndpoint.class);
        DefaultConsumer consumerWithNullProcessor = new DefaultConsumer(endpoint, null);

        AiToolSpec spec = new AiToolSpec(
                "nullProc", "test", Map.of(), null, consumerWithNullProcessor);
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
        assertThat(error.message()).contains("No route processor available for tool 'nullProc'");
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testExecuteReturnsNoResultForNullBody() {
        AiToolSpec spec = findSpec("nullBodyTool");
        Exchange exchange = new DefaultExchange(context);

        AiToolResult result = AiToolExecutor.execute(spec, null, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("Null body should produce 'No result' sentinel")
                .isEqualTo("No result");
    }

    @Test
    public void testArgumentsAccessibleViaHeaders() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Diana");
        arguments.put("age", 28);

        AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(exchange.getMessage().getHeader("name", String.class))
                .as("Argument should be set as exchange header")
                .isEqualTo("Diana");
        assertThat(exchange.getMessage().getHeader("age", Integer.class))
                .as("Integer argument should be set as exchange header")
                .isEqualTo(28);
    }

    @Test
    public void testCamelPrefixedArgumentsRejectedFromHeaders() {
        AiToolSpec spec = findSpec("noParams");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("CamelOverrideEndpoint", "http://evil.com");
        arguments.put("camelFoo", "bar");
        arguments.put("org.apache.camel.hack", "value");
        arguments.put("safeParam", "ok");

        AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(exchange.getMessage().getHeader("CamelOverrideEndpoint"))
                .as("Camel-prefixed argument must not be set as header")
                .isNull();
        assertThat(exchange.getMessage().getHeader("camelFoo"))
                .as("camel-prefixed argument must not be set as header")
                .isNull();
        assertThat(exchange.getMessage().getHeader("org.apache.camel.hack"))
                .as("org.apache.camel. prefixed argument must not be set as header")
                .isNull();
        assertThat(exchange.getMessage().getHeader("safeParam", String.class))
                .as("Non-Camel argument should be set as header")
                .isEqualTo("ok");
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
