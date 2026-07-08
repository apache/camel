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
package org.apache.camel.component.a2a;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.Part;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.state.A2ATaskStore;
import org.apache.camel.component.a2a.state.InMemoryTaskStore;
import org.apache.camel.model.A2ASubTaskDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2ASubTaskTest extends CamelTestSupport {

    A2ASubTaskTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    void emitsBeforeAndAfterAroundSuccessfulNestedSteps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("success")
                        .a2aSubTask()
                        .emitBefore("Searching ${body}")
                        .emitAfter("Done ${body}")
                        .emitOnError("Failed ${exception.message}")
                        .setBody(simple("${body}-found"))
                        .end();
            }
        });
        context.start();

        RouteDefinition route = context.getRouteDefinition("success");
        assertThat(route.getOutputs().get(0)).isInstanceOf(A2ASubTaskDefinition.class);

        List<StreamResponse> received = subscribeToProgress("success");

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "success");

        assertThat(result).isEqualTo("docs-found");
        assertThat(progressMessages(received)).containsExactly("Searching docs", "Done docs-found");
    }

    @Test
    void emitsOnErrorAndKeepsOriginalException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("failure")
                        .a2aSubTask()
                        .emitBefore("Before ${body}")
                        .emitAfter("After ${body}")
                        .emitOnError("Failed ${exception.message}")
                        .throwException(IllegalStateException.class, "broken")
                        .end();
            }
        });
        context.start();

        List<StreamResponse> received = subscribeToProgress("failure");

        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "failure"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessage("broken");
        assertThat(progressMessages(received)).containsExactly("Before docs", "Failed broken");
    }

    @Test
    void progressStoreFailureBeforeRunsNestedSteps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("store-before")
                        .a2aSubTask()
                        .emitBefore("Before ${body}")
                        .setBody(simple("${body}-done"))
                        .end();
            }
        });
        FailingStatusUpdateTaskStore store = addFailingProgressEndpoint("store-before");
        context.start();

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "store-before");

        assertThat(result).isEqualTo("docs-done");
        assertThat(store.updateAttempts).isEqualTo(1);
    }

    @Test
    void progressStoreFailureAfterKeepsSuccessfulResult() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("store-after")
                        .a2aSubTask()
                        .emitAfter("After ${body}")
                        .setBody(simple("${body}-done"))
                        .end();
            }
        });
        FailingStatusUpdateTaskStore store = addFailingProgressEndpoint("store-after");
        context.start();

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "store-after");

        assertThat(result).isEqualTo("docs-done");
        assertThat(store.updateAttempts).isEqualTo(1);
    }

    @Test
    void progressStoreFailureOnErrorKeepsOriginalException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("store-on-error")
                        .a2aSubTask()
                        .emitOnError("Failed ${exception.message}")
                        .throwException(IllegalStateException.class, "broken")
                        .end();
            }
        });
        FailingStatusUpdateTaskStore store = addFailingProgressEndpoint("store-on-error");
        context.start();

        assertThatThrownBy(() -> template.requestBodyAndHeader(
                "direct:start", "docs", A2AConstants.TASK_ID, "store-on-error"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(e -> {
                    assertThat(e.getCause()).hasMessage("broken");
                    assertThat(e.getCause().getSuppressed()).isEmpty();
                });
        assertThat(store.updateAttempts).isEqualTo(1);
    }

    @Test
    void emitBeforeExpressionFailureFailsBeforeNestedSteps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("before-expression-failure")
                        .a2aSubTask()
                        .emitBefore("${mandatoryBodyAs(int)}")
                        .to("mock:result")
                        .end();
            }
        });
        context.start();
        getMockEndpoint("mock:result").expectedMessageCount(0);

        assertThatThrownBy(() -> template.requestBody("direct:start", "docs"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageContaining("No body available of type: int");
        getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    void emitAfterExpressionFailureFailsSuccessfulExchange() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("after-expression-failure")
                        .a2aSubTask()
                        .emitAfter("${mandatoryBodyAs(int)}")
                        .setBody(constant("not-an-integer"))
                        .end();
            }
        });
        context.start();

        assertThatThrownBy(() -> template.requestBody("direct:start", "docs"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageContaining("No body available of type: int");
    }

    @Test
    void emitOnErrorExpressionFailureIsSuppressedOnOriginalException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("on-error-expression-failure")
                        .a2aSubTask()
                        .emitOnError("${mandatoryBodyAs(int)}")
                        .throwException(IllegalStateException.class, "broken")
                        .end();
            }
        });
        context.start();

        assertThatThrownBy(() -> template.requestBody("direct:start", "docs"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(e -> {
                    assertThat(e.getCause()).hasMessage("broken");
                    assertThat(e.getCause().getSuppressed()).hasSize(1);
                });
    }

    @Test
    void supportsNoEmitFields() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("no-emit")
                        .a2aSubTask()
                        .setBody(constant("done"))
                        .end();
            }
        });
        context.start();

        List<StreamResponse> received = subscribeToProgress("no-emit");

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "no-emit");

        assertThat(result).isEqualTo("done");
        assertThat(received).isEmpty();
    }

    @Test
    void withoutTaskContextDefaultsToNoOpAndRunsNestedSteps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("no-context")
                        .a2aSubTask()
                        .emitBefore("Before ${body}")
                        .emitAfter("After ${body}")
                        .setBody(simple("${body}-done"))
                        .end();
            }
        });
        context.start();

        Object result = template.requestBody("direct:start", "docs");

        assertThat(result).isEqualTo("docs-done");
    }

    @Test
    void failIfNoTaskContextFailsBeforeNestedStepsWithoutTaskContext() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("strict-no-context")
                        .a2aSubTask()
                        .failIfNoTaskContext(true)
                        .emitBefore("Before ${body}")
                        .setBody(simple("${body}-should-not-run"))
                        .end();
            }
        });
        context.start();

        assertThatThrownBy(() -> template.requestBody("direct:start", "docs"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("active A2A task context");
    }

    @Test
    void failIfNoTaskContextAllowsDelegatedRouteWithTaskContext() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("strict-delegated")
                        .a2aSubTask()
                        .failIfNoTaskContext(true)
                        .emitBefore("Before ${body}")
                        .emitAfter("After ${body}")
                        .setBody(simple("${body}-delegated"))
                        .end();
            }
        });
        context.start();

        List<StreamResponse> received = subscribeToProgress("strict-delegated");

        Object result = template.requestBodyAndHeader(
                "direct:start", "docs", A2AConstants.TASK_ID, "strict-delegated");

        assertThat(result).isEqualTo("docs-delegated");
        assertThat(progressMessages(received)).containsExactly("Before docs", "After docs-delegated");
    }

    @Test
    void supportsOnlyOneEmitField() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("one-emit")
                        .a2aSubTask()
                        .emitBefore("Only before ${body}")
                        .setBody(constant("done"))
                        .end();
            }
        });
        context.start();

        List<StreamResponse> received = subscribeToProgress("one-emit");

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "one-emit");

        assertThat(result).isEqualTo("done");
        assertThat(progressMessages(received)).containsExactly("Only before docs");
    }

    @Test
    void supportsMultipleNestedSteps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("multi-step")
                        .a2aSubTask()
                        .emitAfter("Finished ${body}")
                        .setBody(simple("${body}-one"))
                        .setBody(simple("${body}-two"))
                        .end();
            }
        });
        context.start();

        List<StreamResponse> received = subscribeToProgress("multi-step");

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "multi-step");

        assertThat(result).isEqualTo("docs-one-two");
        assertThat(progressMessages(received)).containsExactly("Finished docs-one-two");
    }

    @Test
    void javaDslExposesA2ASubTaskAsModelStepAndRunsNestedSteps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("java-dsl")
                        .a2aSubTask()
                        .emitBefore("Java before ${body}")
                        .emitAfter("Java after ${body}")
                        .setBody(simple("${body}-java"))
                        .end();
            }
        });
        context.start();

        RouteDefinition route = context.getRouteDefinition("java-dsl");
        assertThat(route.getOutputs()).hasSize(1);
        assertThat(route.getOutputs().get(0)).isInstanceOf(A2ASubTaskDefinition.class);

        List<StreamResponse> received = subscribeToProgress("java-dsl");

        Object result = template.requestBodyAndHeader("direct:start", "docs", A2AConstants.TASK_ID, "java-dsl");

        assertThat(result).isEqualTo("docs-java");
        assertThat(progressMessages(received)).containsExactly("Java before docs", "Java after docs-java");
    }

    private List<StreamResponse> subscribeToProgress(String taskId) throws Exception {
        List<StreamResponse> received = new CopyOnWriteArrayList<>();
        A2AEndpoint endpoint = createEndpointWithTask(taskId);
        endpoint.getTaskStore().addSubscriber(taskId, (id, event) -> received.add(event));
        context.addEndpoint(endpoint.getEndpointUri(), endpoint);
        return received;
    }

    private FailingStatusUpdateTaskStore addFailingProgressEndpoint(String taskId) throws Exception {
        FailingStatusUpdateTaskStore store = new FailingStatusUpdateTaskStore();
        store.put(taskId, createTask(taskId));
        context.addEndpoint("a2a:progress-" + taskId, createEndpointWithTaskStore(taskId, store));
        return store;
    }

    private A2AEndpoint createEndpointWithTask(String taskId) throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        A2AEndpoint endpoint = createEndpointWithTaskStore(taskId, store);
        store.put(taskId, createTask(taskId));
        return endpoint;
    }

    private A2AEndpoint createEndpointWithTaskStore(String taskId, A2ATaskStore store) throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Progress Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:progress-" + taskId, component, config) {
            @Override
            A2ATaskStore getTaskStore() {
                return store;
            }
        };
        endpoint.setAgentCardSource("progress-" + taskId);
        endpoint.setCamelContext(context);
        endpoint.start();
        return endpoint;
    }

    private static Task createTask(String taskId) {
        return Task.builder()
                .id(taskId)
                .contextId("ctx-" + taskId)
                .status(new TaskStatus(TaskState.WORKING))
                .build();
    }

    private static List<String> progressMessages(List<StreamResponse> responses) {
        return responses.stream()
                .map(A2ASubTaskTest::progressMessage)
                .toList();
    }

    private static String progressMessage(StreamResponse response) {
        Message message = response.getStatusUpdate().status().message();
        Part<?> part = message.parts().get(0);
        return ((TextPart) part).text();
    }

    private static final class FailingStatusUpdateTaskStore extends InMemoryTaskStore {
        private int updateAttempts;

        @Override
        public void updateStatusAndNotify(String taskId, TaskStatus status) {
            updateAttempts++;
            throw new IllegalStateException("progress store unavailable");
        }
    }
}
