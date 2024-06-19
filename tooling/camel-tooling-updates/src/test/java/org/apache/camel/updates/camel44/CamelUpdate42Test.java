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
package org.apache.camel.updates.camel44;

import org.apache.camel.updates.CamelTestUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.properties.Assertions;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

public class CamelUpdate42Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        CamelTestUtil.recipe(spec, CamelTestUtil.CamelVersion.v4_4)
                .parser(CamelTestUtil.parserFromClasspath(CamelTestUtil.CamelVersion.v4_0, "camel-api",
                        "camel-json-validator", "camel-support", "camel-saga"))
                .typeValidationOptions(TypeValidation.none());
    }

    /**
     * The option camel.main.debugger has been renamed to camel.debug.enabled.
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_2.html#_camel_main">The documentation</a>
     */
    @Test
    void testCamelMainDebugger() {
        rewriteRun(Assertions.properties("""
                   #test
                   quarkus.camel.main.debugger=true
                   camel.main.debugger=true
                """,
                """
                            #test
                            quarkus.camel.debug.enabled=true
                            camel.debug.enabled=true
                        """));
    }

    /**
     * The org.apache.camel.service.lra.LRAClient can now access Exchange to retrieve further context information.
     * Therefore, there are following changes in interface methods
     * <ul>
     * <li>org.apache.camel.saga.CamelSagaService.compensate() changed to
     * org.apache.camel.saga.CamelSagaService.compensate(Exchange exchange)</li>
     * <li>org.apache.camel.saga.CamelSagaService.complete() changed to
     * org.apache.camel.saga.CamelSagaService.complete(Exchange exchange)</li>
     * <li>org.apache.camel.saga.CamelSagaCoordinator.newSaga is now
     * org.apache.camel.saga.CamelSagaCoordinator.newSaga(Exchange exchange) to support the transport of Exchange.</li>
     * </ul>
     * As result of interface changes also the known implementation classes and usages have been adopted.
     * <p/>
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_2.html#_camel_saga_camel_lra">The
     * documentation</a>
     *
     */
    @Test
    void testAddedExchangeIntoSaga() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.CamelContext;
                    import org.apache.camel.builder.RouteBuilder;
                    import org.apache.camel.saga.CamelSagaCoordinator;
                    import org.apache.camel.saga.InMemorySagaCoordinator;
                    import org.apache.camel.saga.InMemorySagaService;
                    import java.util.concurrent.CompletableFuture;

                    public class Saga01Test extends RouteBuilder {

                        CamelContext camelContext;

                        @Override
                        public void configure()  {
                            InMemorySagaService sagaService = new InMemorySagaService();
                            CompletableFuture<CamelSagaCoordinator> s = sagaService.newSaga();
                            try {
                                getContext().addService(sagaService);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            String uuid = camelContext.getUuidGenerator().generateUuid();

                            CamelSagaCoordinator coordinator = new InMemorySagaCoordinator(camelContext, sagaService, uuid);
                            coordinator.compensate();
                            coordinator.complete();
                        }
                    }
                """,
                """
                        import org.apache.camel.CamelContext;
                        import org.apache.camel.builder.RouteBuilder;
                        import org.apache.camel.saga.CamelSagaCoordinator;
                        import org.apache.camel.saga.InMemorySagaCoordinator;
                        import org.apache.camel.saga.InMemorySagaService;
                        import java.util.concurrent.CompletableFuture;

                        public class Saga01Test extends RouteBuilder {

                            CamelContext camelContext;

                            @Override
                            public void configure()  {
                                InMemorySagaService sagaService = new InMemorySagaService();
                                CompletableFuture<CamelSagaCoordinator> s = sagaService.newSaga(/*Exchange parameter was added.*/(Exchange) null);
                                try {
                                    getContext().addService(sagaService);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                String uuid = camelContext.getUuidGenerator().generateUuid();

                                CamelSagaCoordinator coordinator = new InMemorySagaCoordinator(camelContext, sagaService, uuid);
                                coordinator.compensate(/*Exchange parameter was added.*/(Exchange) null);
                                coordinator.complete(/*Exchange parameter was added.*/(Exchange) null);
                            }
                        }
                            """));
    }

}
