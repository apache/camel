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
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that exercise realistic AI tool route scenarios end-to-end through the executor, using bean
 * services to simulate real backends (database lookups, calculations, etc.).
 */
public class AiToolIntegrationTest extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("userService", new UserService());
        registry.bind("calculator", new CalculatorService());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:lookupUser"
                     + "?tags=users"
                     + "&description=Look up a user by ID"
                     + "&parameter.id=integer"
                     + "&parameter.id.description=The user ID"
                     + "&parameter.id.required=true")
                        .to("bean:userService?method=findById(${header.id})");

                from("ai-tool:calculate"
                     + "?tags=math"
                     + "&description=Perform a math operation"
                     + "&parameter.a=number"
                     + "&parameter.a.description=First operand"
                     + "&parameter.a.required=true"
                     + "&parameter.b=number"
                     + "&parameter.b.description=Second operand"
                     + "&parameter.b.required=true"
                     + "&parameter.operation=string"
                     + "&parameter.operation.description=The operation"
                     + "&parameter.operation.enum=add,subtract,multiply,divide"
                     + "&parameter.operation.required=true")
                        .to("bean:calculator?method=compute(${header.a}, ${header.b}, ${header.operation})");

                from("ai-tool:searchUsers"
                     + "?tags=users"
                     + "&description=Search users by name prefix"
                     + "&parameter.prefix=string"
                     + "&parameter.prefix.description=Name prefix to search"
                     + "&parameter.prefix.required=true")
                        .to("bean:userService?method=searchByPrefix(${header.prefix})");

                from("ai-tool:dynamicLookup"
                     + "?tags=users"
                     + "&description=Look up a user by ID using dynamic endpoint"
                     + "&parameter.id=integer"
                     + "&parameter.id.description=The user ID"
                     + "&parameter.id.required=true")
                        .toD("bean:userService?method=findById(${header.id})");
            }
        };
    }

    @Test
    public void testBeanLookupByHeaderId() {
        AiToolSpec spec = findSpec("lookupUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 1);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result)
                .as("Bean lookup via header should succeed")
                .isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("Bean should resolve id from exchange header")
                .isEqualTo("Alice (id=1)");
    }

    @Test
    public void testBeanLookupWithUnknownId() {
        AiToolSpec spec = findSpec("lookupUser");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 999);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("Unknown ID should return not-found message")
                .isEqualTo("User not found");
    }

    @Test
    public void testCalculatorAdd() {
        AiToolSpec spec = findSpec("calculate");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("a", 10.5);
        arguments.put("b", 3.2);
        arguments.put("operation", "add");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("Bean should receive all three arguments from headers")
                .isEqualTo("13.7");
    }

    @Test
    public void testCalculatorDivide() {
        AiToolSpec spec = findSpec("calculate");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("a", 20.0);
        arguments.put("b", 4.0);
        arguments.put("operation", "divide");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .isEqualTo("5.0");
    }

    @Test
    public void testCalculatorDivideByZero() {
        AiToolSpec spec = findSpec("calculate");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("a", 10.0);
        arguments.put("b", 0.0);
        arguments.put("operation", "divide");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
        assertThat(error.message()).contains("Division by zero");
        assertThat(error.cause()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void testCalculatorUnknownOperation() {
        AiToolSpec spec = findSpec("calculate");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("a", 10.0);
        arguments.put("b", 5.0);
        arguments.put("operation", "modulo");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.ExecutionError.class);
        AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
        assertThat(error.message()).contains("Unknown operation");
        assertThat(error.cause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDynamicEndpointLookup() {
        AiToolSpec spec = findSpec("dynamicLookup");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 2);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result)
                .as("toD should resolve header in endpoint URI")
                .isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .isEqualTo("Bob (id=2)");
    }

    @Test
    public void testDynamicEndpointLookupUnknownId() {
        AiToolSpec spec = findSpec("dynamicLookup");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 42);

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .isEqualTo("User not found");
    }

    @Test
    public void testSearchByPrefix() {
        AiToolSpec spec = findSpec("searchUsers");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("prefix", "A");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("Should return all users matching prefix, sorted alphabetically")
                .isEqualTo("Adam, Alice");
    }

    @Test
    public void testSearchByPrefixNoMatch() {
        AiToolSpec spec = findSpec("searchUsers");
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("prefix", "Z");

        AiToolResult result = AiToolExecutor.execute(spec, arguments, exchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value())
                .as("No matching users should return not-found message")
                .isEqualTo("No users found");
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getAllTools().stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }

    public static class UserService {

        private static final Map<Integer, String> USERS = Map.of(
                1, "Alice",
                2, "Bob",
                3, "Adam");

        public String findById(int id) {
            String name = USERS.get(id);
            return name != null ? name + " (id=" + id + ")" : "User not found";
        }

        public String searchByPrefix(String prefix) {
            String matches = USERS.values().stream()
                    .filter(name -> name.startsWith(prefix))
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("No users found");
            return matches;
        }
    }

    public static class CalculatorService {

        public String compute(double a, double b, String operation) {
            double result;
            switch (operation) {
                case "add":
                    result = a + b;
                    break;
                case "subtract":
                    result = a - b;
                    break;
                case "multiply":
                    result = a * b;
                    break;
                case "divide":
                    if (b == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    result = a / b;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
            return String.valueOf(result);
        }
    }
}
