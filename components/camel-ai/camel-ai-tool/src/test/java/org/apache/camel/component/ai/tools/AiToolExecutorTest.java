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
package org.apache.camel.component.ai.tools;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                        .process(exchange -> {
                            AiToolArguments args
                                    = exchange.getVariable(AiTool.TOOL_ARGUMENTS, AiToolArguments.class);
                            exchange.getMessage().setBody(
                                    "Hello " + args.getString("name") + ", age " + args.get("age"));
                        });

                from("ai-tool:noParams"
                     + "?tags=test"
                     + "&description=A tool with no parameters")
                        .setBody(constant("no-param result"));

                from("ai-tool:nullBodyTool"
                     + "?tags=test"
                     + "&description=A tool that returns null body")
                        .setBody(constant(null));

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
    public void testArgumentsAccessibleViaVariable() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Carol");
        arguments.put("age", 42);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result)
                .as("Execution should succeed")
                .isInstanceOf(AiToolResult.Success.class);

        AiToolArguments args = exchange.getVariable(AiTool.TOOL_ARGUMENTS, AiToolArguments.class);
        assertThat(args)
                .as("AiToolArguments should be set as exchange variable")
                .isNotNull();
        assertThat(args.getToolName())
                .as("Tool name")
                .isEqualTo("greetUser");
        assertThat(args.getString("name"))
                .as("String argument")
                .isEqualTo("Carol");
        assertThat(args.getInteger("age"))
                .as("Integer argument")
                .isEqualTo(42);
        assertThat(args.has("name"))
                .as("has() for existing argument")
                .isTrue();
        assertThat(args.has("missing"))
                .as("has() for missing argument")
                .isFalse();
    }

    @Test
    public void testArgumentsTypedGettersReturnNullOnBadInput() {
        AiToolArguments args = new AiToolArguments("test", Map.of("bad", "not_a_number"));

        assertThat(args.getInteger("bad"))
                .as("getInteger on non-numeric string should return null")
                .isNull();
        assertThat(args.getDouble("bad"))
                .as("getDouble on non-numeric string should return null")
                .isNull();
        assertThat(args.getBoolean("bad"))
                .as("getBoolean on non-boolean string should return null")
                .isNull();
        assertThat(args.getInteger("missing"))
                .as("getInteger on missing key should return null")
                .isNull();
    }

    @Test
    public void testArgumentsGetBooleanVariants() {
        Map<String, Object> params = new HashMap<>();
        params.put("boolTrue", Boolean.TRUE);
        params.put("boolFalse", Boolean.FALSE);
        params.put("strTrue", "true");
        params.put("strTrueUpper", "TRUE");
        params.put("strFalse", "false");
        params.put("strFalseMixed", "False");
        params.put("strGarbage", "yes");

        AiToolArguments args = new AiToolArguments("test", params);

        assertThat(args.getBoolean("boolTrue")).isTrue();
        assertThat(args.getBoolean("boolFalse")).isFalse();
        assertThat(args.getBoolean("strTrue")).isTrue();
        assertThat(args.getBoolean("strTrueUpper")).isTrue();
        assertThat(args.getBoolean("strFalse")).isFalse();
        assertThat(args.getBoolean("strFalseMixed")).isFalse();
        assertThat(args.getBoolean("strGarbage"))
                .as("non-boolean string should return null, not false")
                .isNull();
        assertThat(args.getBoolean("missing"))
                .as("missing key should return null")
                .isNull();
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
    public void testArgumentsParametersAreImmutable() {
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");
        AiToolArguments args = new AiToolArguments("test", params);

        assertThat(args.getParameters()).containsEntry("key", "value");
        assertThatThrownBy(() -> args.getParameters().put("new", "entry"))
                .as("getParameters() should return an unmodifiable map")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testArgumentsGetDoubleWithNumericInput() {
        Map<String, Object> params = new HashMap<>();
        params.put("pi", 3.14);
        params.put("count", 42);
        params.put("strNum", "2.718");
        AiToolArguments args = new AiToolArguments("test", params);

        assertThat(args.getDouble("pi"))
                .as("Double value should be returned directly")
                .isEqualTo(3.14);
        assertThat(args.getDouble("count"))
                .as("Integer value should be converted to Double")
                .isEqualTo(42.0);
        assertThat(args.getDouble("strNum"))
                .as("Numeric string should be parsed to Double")
                .isEqualTo(2.718);
    }

    @Test
    public void testArgumentsGetStringWithNonStringValue() {
        Map<String, Object> params = new HashMap<>();
        params.put("number", 42);
        params.put("bool", true);
        AiToolArguments args = new AiToolArguments("test", params);

        assertThat(args.getString("number"))
                .as("Integer should be converted via toString()")
                .isEqualTo("42");
        assertThat(args.getString("bool"))
                .as("Boolean should be converted via toString()")
                .isEqualTo("true");
        assertThat(args.getString("missing"))
                .as("Missing key should return null")
                .isNull();
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
