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
package org.apache.camel.dsl.yaml.validator.stub;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.processor.DisabledProcessor;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.reifier.language.ExpressionReifier;

public class StubReifier {

    private StubReifier() {
    }

    public static void registerStubReifiers() {
        ExpressionReifier.registerReifier(MethodCallExpression.class, (camelContext, expressionDefinition) -> {
            if (expressionDefinition instanceof MethodCallExpression) {
                return new ExpressionReifier<>(camelContext, expressionDefinition) {
                    @Override
                    public Expression createExpression() {
                        return ExpressionBuilder.constantExpression(null);
                    }

                    @Override
                    public Predicate createPredicate() {
                        return PredicateBuilder.constant(true);
                    }
                };
            }
            return null;
        });
        ProcessorReifier.registerReifier(BeanDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof BeanDefinition bd) {
                        return new ProcessorReifier<>(route, bd) {
                            @Override
                            public Processor createProcessor() throws Exception {
                                return new DisabledProcessor();
                            }
                        };
                    }
                    return null;
                });
        ProcessorReifier.registerReifier(CircuitBreakerDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof CircuitBreakerDefinition cb) {
                        return new ProcessorReifier<>(route, cb) {
                            @Override
                            public Processor createProcessor() throws Exception {
                                return new DisabledProcessor();
                            }
                        };
                    }
                    return null;
                });
        ProcessorReifier.registerReifier(SagaDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof SagaDefinition sd) {
                        return new ProcessorReifier<>(route, sd) {
                            @Override
                            public Processor createProcessor() throws Exception {
                                return new DisabledProcessor();
                            }
                        };
                    }
                    return null;
                });
    }

}
