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
package org.apache.camel.component.langchain4j.agent.api;

import java.util.Collections;
import java.util.List;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompositeToolProviderTest {

    private static final ToolExecutor NOOP_EXECUTOR = (request, memoryId) -> "ok";
    private static final ToolProviderRequest DUMMY_REQUEST = new ToolProviderRequest("test", UserMessage.from("test"));

    @Test
    public void testSingleProvider() {
        ToolSpecification spec = ToolSpecification.builder().name("tool1").description("A tool").build();

        ToolProvider provider = request -> ToolProviderResult.builder()
                .add(spec, NOOP_EXECUTOR)
                .build();

        CompositeToolProvider composite = new CompositeToolProvider(List.of(provider));
        ToolProviderResult result = composite.provideTools(DUMMY_REQUEST);

        assertNotNull(result);
        assertEquals(1, result.aiServiceTools().size());
        assertEquals(spec, toolByName(result, "tool1").toolSpecification());
    }

    @Test
    public void testMultipleProviders() {
        ToolSpecification spec1 = ToolSpecification.builder().name("tool1").description("Tool 1").build();
        ToolSpecification spec2 = ToolSpecification.builder().name("tool2").description("Tool 2").build();

        ToolProvider provider1 = request -> ToolProviderResult.builder()
                .add(spec1, NOOP_EXECUTOR)
                .build();

        ToolProvider provider2 = request -> ToolProviderResult.builder()
                .add(spec2, NOOP_EXECUTOR)
                .build();

        CompositeToolProvider composite = new CompositeToolProvider(List.of(provider1, provider2));
        ToolProviderResult result = composite.provideTools(DUMMY_REQUEST);

        assertNotNull(result);
        assertEquals(2, result.aiServiceTools().size());
        assertEquals(spec1, toolByName(result, "tool1").toolSpecification());
        assertEquals(spec2, toolByName(result, "tool2").toolSpecification());
    }

    @Test
    public void testEmptyProviderList() {
        CompositeToolProvider composite = new CompositeToolProvider(Collections.emptyList());
        ToolProviderResult result = composite.provideTools(DUMMY_REQUEST);

        assertNotNull(result);
        assertTrue(result.aiServiceTools().isEmpty());
    }

    @Test
    public void testDuplicateToolNamesThrows() {
        ToolSpecification spec1 = ToolSpecification.builder().name("sameName").description("Tool 1").build();
        ToolSpecification spec2 = ToolSpecification.builder().name("sameName").description("Tool 2").build();

        ToolProvider provider1 = request -> ToolProviderResult.builder()
                .add(spec1, NOOP_EXECUTOR)
                .build();

        ToolProvider provider2 = request -> ToolProviderResult.builder()
                .add(spec2, NOOP_EXECUTOR)
                .build();

        CompositeToolProvider composite = new CompositeToolProvider(List.of(provider1, provider2));

        // LangChain4j ToolProviderResult.Builder throws on duplicate names
        assertThrows(Exception.class, () -> composite.provideTools(DUMMY_REQUEST));
    }

    @Test
    public void testImmediateReturnToolNamesMerged() {
        ToolSpecification spec1 = ToolSpecification.builder().name("tool1").description("Tool 1").build();
        ToolSpecification spec2 = ToolSpecification.builder().name("tool2").description("Tool 2").build();

        ToolProvider provider1 = request -> ToolProviderResult.builder()
                .add(spec1, NOOP_EXECUTOR, ReturnBehavior.IMMEDIATE)
                .build();

        ToolProvider provider2 = request -> ToolProviderResult.builder()
                .add(spec2, NOOP_EXECUTOR, ReturnBehavior.IMMEDIATE)
                .build();

        CompositeToolProvider composite = new CompositeToolProvider(List.of(provider1, provider2));
        ToolProviderResult result = composite.provideTools(DUMMY_REQUEST);

        assertNotNull(result);
        assertEquals(2, result.aiServiceTools().size());
        assertEquals(ReturnBehavior.IMMEDIATE, toolByName(result, "tool1").returnBehavior());
        assertEquals(ReturnBehavior.IMMEDIATE, toolByName(result, "tool2").returnBehavior());
    }

    @Test
    public void testProviderWithNoTools() {
        ToolSpecification spec1 = ToolSpecification.builder().name("tool1").description("Tool 1").build();

        ToolProvider provider1 = request -> ToolProviderResult.builder()
                .add(spec1, NOOP_EXECUTOR)
                .build();

        ToolProvider emptyProvider = request -> ToolProviderResult.builder().build();

        CompositeToolProvider composite = new CompositeToolProvider(List.of(provider1, emptyProvider));
        ToolProviderResult result = composite.provideTools(DUMMY_REQUEST);

        assertNotNull(result);
        assertEquals(1, result.aiServiceTools().size());
        assertEquals(spec1, toolByName(result, "tool1").toolSpecification());
    }

    private static AiServiceTool toolByName(ToolProviderResult result, String name) {
        return result.aiServiceTools().stream()
                .filter(t -> name.equals(t.name()))
                .findFirst()
                .orElseThrow();
    }
}
