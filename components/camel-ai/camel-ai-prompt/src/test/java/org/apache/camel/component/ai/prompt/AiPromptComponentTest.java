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
package org.apache.camel.component.ai.prompt;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptComponentTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-prompt:greeting?tags=tests&description=Greeting"
                     + "&argument.name.description=The person to greet"
                     + "&argument.name.required=true")
                        .routeId("greeting-route")
                        .setBody(simple("Hello ${header.name}"));

                from("ai-prompt:bytes?tags=tests")
                        .setBody(constant("byte body".getBytes(StandardCharsets.UTF_8)));

                from("ai-prompt:stream?tags=tests")
                        .setBody(constant(new ByteArrayInputStream("stream body".getBytes(StandardCharsets.UTF_8))));

                from("ai-prompt:ordered?tags=tests&argument.second.description=Second"
                     + "&argument.first.description=First")
                        .setBody(constant("ordered"));

                from("ai-prompt:suspendable?tags=tests&description=A prompt whose route is suspended and resumed")
                        .routeId("suspendable-route")
                        .setBody(constant("suspendable result"));
            }
        };
    }

    @Test
    void promptIsRegisteredAndExecutesWithStringArguments() {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(context);
        AiPromptSpec spec = registry.getPromptsByTag("tests").stream()
                .filter(prompt -> "greeting".equals(prompt.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(spec.getArguments()).containsExactly(
                new AiPromptArgument("name", "The person to greet", true));

        AiPromptResult result = AiPromptExecutor.execute(spec, Map.of("name", "Camel"),
                spec.getConsumer().getEndpoint().createExchange());

        assertThat(result).isInstanceOf(AiPromptResult.Success.class);
        assertThat(((AiPromptResult.Success) result).value()).isEqualTo("Hello Camel");
    }

    @Test
    void byteArrayAndStreamBodiesAreConvertedToText() {
        AiPromptRegistry registry = AiPromptRegistry.getOrCreate(context);
        AiPromptSpec bytes = registry.getPromptsByTag("tests").stream()
                .filter(prompt -> "bytes".equals(prompt.getName())).findFirst().orElseThrow();
        AiPromptSpec stream = registry.getPromptsByTag("tests").stream()
                .filter(prompt -> "stream".equals(prompt.getName())).findFirst().orElseThrow();

        assertThat(((AiPromptResult.Success) AiPromptExecutor.execute(
                bytes, Map.of(), bytes.getConsumer().getEndpoint().createExchange())).value()).isEqualTo("byte body");
        assertThat(((AiPromptResult.Success) AiPromptExecutor.execute(
                stream, Map.of(), stream.getConsumer().getEndpoint().createExchange())).value()).isEqualTo("stream body");
    }

    @Test
    void declaredArgumentsKeepUriOrder() {
        AiPromptSpec spec = AiPromptRegistry.getOrCreate(context).getPromptsByTag("tests").stream()
                .filter(prompt -> "ordered".equals(prompt.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(spec.getArguments()).extracting(AiPromptArgument::name)
                .containsExactly("second", "first");
    }

    @Test
    void missingRequiredArgumentIsReportedBeforeRouteExecution() {
        AiPromptSpec spec = AiPromptRegistry.getOrCreate(context).getPromptsByTag("tests").stream()
                .filter(prompt -> "greeting".equals(prompt.getName()))
                .findFirst()
                .orElseThrow();

        AiPromptResult result = AiPromptExecutor.execute(
                spec, Map.of(), spec.getConsumer().getEndpoint().createExchange());

        assertThat(result).isInstanceOf(AiPromptResult.ArgumentError.class);
        assertThat(((AiPromptResult.ArgumentError) result).message())
                .contains("Missing required argument 'name'");
    }

    @Test
    void suspendedPromptRefusesRequestsUntilItsRouteResumes() throws Exception {
        // a caller can hold a spec it obtained before the route was suspended
        AiPromptSpec spec = findSpec("suspendable");
        context.getRouteController().suspendRoute("suspendable-route");

        AiPromptResult refused = AiPromptExecutor.execute(
                spec, Map.of(), spec.getConsumer().getEndpoint().createExchange());

        assertThat(refused).isInstanceOf(AiPromptResult.ExecutionError.class);
        AiPromptResult.ExecutionError error = (AiPromptResult.ExecutionError) refused;
        assertThat(error.message()).isEqualTo("Prompt 'suspendable' is suspended");
        assertThat(error.cause()).isInstanceOf(IllegalStateException.class);

        context.getRouteController().resumeRoute("suspendable-route");

        assertThat(findSpec("suspendable")).isSameAs(spec);
        AiPromptResult result = AiPromptExecutor.execute(
                spec, Map.of(), spec.getConsumer().getEndpoint().createExchange());
        assertThat(result).isInstanceOf(AiPromptResult.Success.class);
        assertThat(((AiPromptResult.Success) result).value()).isEqualTo("suspendable result");
    }

    private AiPromptSpec findSpec(String promptName) {
        return AiPromptRegistry.getOrCreate(context).getPromptsByTag("tests").stream()
                .filter(prompt -> promptName.equals(prompt.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Prompt not found: " + promptName));
    }
}
