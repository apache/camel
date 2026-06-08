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
package org.apache.camel.component.langchain4j.agent.integration;

import java.io.FileNotFoundException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for jsonSchema error handling in the langchain4j-agent component. These tests do not require a running LLM
 * and always run in CI. For happy-path integration tests that exercise structured output end-to-end, see
 * {@link LangChain4jAgentStructuredOutputIT}.
 *
 * <p>
 * Each test creates its own {@link org.apache.camel.impl.DefaultCamelContext} instead of using
 * {@link org.apache.camel.test.junit6.CamelTestSupport}. This is intentional: the jsonSchema error is detected during
 * producer initialisation ({@code doInit()}), which runs as part of context startup. In CamelTestSupport, context
 * startup happens inside {@code setUp()} — before the {@code @Test} method — so a startup failure would abort the test
 * before {@code assertThrows} could capture it. Owning the context lifecycle inside the test method keeps the exception
 * within the scope of {@code assertThrows}.
 */
public class LangChain4jAgentStructuredOutputTest {

    @Test
    void testNonExistentClasspathSchemaThrowsFileNotFoundException() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            AgentConfiguration config = new AgentConfiguration();
            context.getRegistry().bind("myConfig", config);

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:test")
                            .to("langchain4j-agent:test?agentConfiguration=#myConfig&jsonSchema=classpath:non-existent-schema.json");
                }
            });

            // FailedToStartRouteException -> RuntimeCamelException -> FileNotFoundException
            Exception ex = assertThrows(Exception.class, context::start);
            assertInstanceOf(FileNotFoundException.class, ex.getCause().getCause());
        }
    }

    @Test
    void testNonExistentFileSchemaThrowsFileNotFoundException() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            AgentConfiguration config = new AgentConfiguration();
            context.getRegistry().bind("myConfig", config);

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:test")
                            .to("langchain4j-agent:test?agentConfiguration=#myConfig&jsonSchema=file:/tmp/non-existent-schema-12345.json");
                }
            });

            // FailedToStartRouteException -> RuntimeCamelException -> FileNotFoundException
            Exception ex = assertThrows(Exception.class, context::start);
            assertInstanceOf(FileNotFoundException.class, ex.getCause().getCause());
        }
    }

    @Test
    void testInvalidJsonContentThrowsIllegalArgumentException() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            AgentConfiguration config = new AgentConfiguration();
            context.getRegistry().bind("myConfig", config);

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:test")
                            .to("langchain4j-agent:test?agentConfiguration=#myConfig&jsonSchema=RAW(this is not json)");
                }
            });

            // FailedToStartRouteException -> RuntimeCamelException -> IllegalArgumentException
            Exception ex = assertThrows(Exception.class, context::start);
            assertInstanceOf(IllegalArgumentException.class, ex.getCause().getCause());
        }
    }

}
