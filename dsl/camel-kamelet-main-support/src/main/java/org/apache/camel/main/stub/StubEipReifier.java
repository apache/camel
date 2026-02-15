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
package org.apache.camel.main.stub;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.processor.DisabledProcessor;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.reifier.language.ExpressionReifier;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.support.PluginHelper;

public class StubEipReifier {

    private StubEipReifier() {
    }

    public static void registerStubEipReifiers(final CamelContext camelContext) {

        // bean language (method call) refers to custom classes which we don't want to load or require being on classpath
        ExpressionReifier.registerReifier(MethodCallExpression.class, (context, expressionDefinition) -> {
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
                // bean refers to custom classes which we don't want to load or require being on classpath
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

        ProcessorReifier.registerReifier(ThrowExceptionDefinition.class,
                // throw exception to custom classes which we don't want to load or require being on classpath
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof ThrowExceptionDefinition td) {
                        return new ProcessorReifier<>(route, td) {
                            @Override
                            public Processor createProcessor() throws Exception {
                                return new DisabledProcessor();
                            }
                        };
                    }
                    return null;
                });

        // kamelet EIP should be stubbed
        ProcessorReifier.registerReifier(KameletDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof KameletDefinition kd) {
                        return new ProcessorReifier<>(route, kd) {
                            @Override
                            public Processor createProcessor() throws Exception {
                                return new DisabledProcessor();
                            }
                        };
                    }
                    return null;
                });
        // stub kamelet process factory also
        final ProcessorFactory fac = PluginHelper.getProcessorFactory(camelContext);
        camelContext.getCamelContextExtension().addContextPlugin(ProcessorFactory.class, new ProcessorFactory() {
            @Override
            public Processor createChildProcessor(Route route, NamedNode definition, boolean mandatory) throws Exception {
                if (definition instanceof KameletDefinition) {
                    return new DisabledProcessor();
                }
                return fac.createChildProcessor(route, definition, mandatory);
            }

            @Override
            public Processor createProcessor(Route route, NamedNode definition) throws Exception {
                if (definition instanceof KameletDefinition) {
                    return new DisabledProcessor();
                }
                return fac.createProcessor(route, definition);
            }

            @Override
            public Processor createProcessor(CamelContext camelContext, String definitionName, Object[] args) throws Exception {
                if ("kamelet".equals(definitionName)) {
                    return new DisabledProcessor();
                }
                return fac.createProcessor(camelContext, definitionName, args);
            }
        });
    }

}
