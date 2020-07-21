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
package org.apache.camel.cdi;

import java.util.function.Function;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.Processor;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.cdi.xml.ErrorHandlerDefinition;
import org.apache.camel.cdi.xml.RedeliveryPolicyFactoryBean;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.apache.camel.cdi.BeanManagerHelper.getReferenceByName;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

final class XmlErrorHandlerFactoryBean extends SyntheticBean<ErrorHandlerBuilder> {

    private final BeanManager manager;

    private final ErrorHandlerDefinition handler;

    XmlErrorHandlerFactoryBean(BeanManager manager, SyntheticAnnotated annotated, Class<?> type, Function<Bean<ErrorHandlerBuilder>, String> toString, ErrorHandlerDefinition handler) {
        super(manager, annotated, type, null, toString);
        this.manager = manager;
        this.handler = handler;
    }

    @Override
    public ErrorHandlerBuilder create(CreationalContext<ErrorHandlerBuilder> creationalContext) {
        try {
            ErrorHandlerBuilder builder = handler.getType().getTypeAsClass().newInstance();

            switch (handler.getType()) {
                case DefaultErrorHandler:
                case DeadLetterChannel:
                    setProperties((DefaultErrorHandlerBuilder) builder);
                    break;
                case NoErrorHandler:
                    // No configuration required
                    break;
                case TransactionErrorHandler:
                    break;
                default:
                    break;
            }

            return builder;
        } catch (Exception cause) {
            throw new CreationException("Error while creating instance for " + this, cause);
        }
    }

    @Override
    public void destroy(ErrorHandlerBuilder instance, CreationalContext<ErrorHandlerBuilder> creationalContext) {
        // NOOP
    }

    private void setProperties(DefaultErrorHandlerBuilder builder) throws Exception {
        if (nonNull(handler.getDeadLetterHandleNewException())) {
            builder.setDeadLetterHandleNewException(Boolean.parseBoolean(handler.getDeadLetterHandleNewException()));
        }
        builder.setDeadLetterUri(handler.getDeadLetterUri());
        builder.setExecutorServiceRef(handler.getExecutorServiceRef());
        builder.setRetryWhileRef(handler.getRetryWhileRef());
        if (nonNull(handler.getUseOriginalMessage())) {
            builder.setUseOriginalMessage(parseBoolean(handler.getUseOriginalMessage()));
        }
        if (nonNull(handler.getUseOriginalBody())) {
            builder.setUseOriginalBody(Boolean.parseBoolean(handler.getUseOriginalBody()));
        }

        if (isNotEmpty(handler.getOnExceptionOccurredRef())) {
            Processor processor = getReferenceByName(manager, handler.getOnExceptionOccurredRef(), Processor.class)
                    .orElseThrow(() -> new UnsatisfiedResolutionException(
                            format("No bean with name [%s] to satisfy attribute [%s]",
                                    handler.getOnPrepareFailureRef(), "onExceptionOccurredRef")));
            builder.setOnExceptionOccurred(processor);
        }

        if (isNotEmpty(handler.getOnPrepareFailureRef())) {
            Processor processor = getReferenceByName(manager, handler.getOnPrepareFailureRef(), Processor.class)
                    .orElseThrow(() -> new UnsatisfiedResolutionException(
                            format("No bean with name [%s] to satisfy attribute [%s]",
                                    handler.getOnPrepareFailureRef(), "onPrepareFailureRef")));
            builder.setOnPrepareFailure(processor);
        }

        if (isNotEmpty(handler.getOnRedeliveryRef())) {
            Processor processor = getReferenceByName(manager, handler.getOnRedeliveryRef(), Processor.class)
                    .orElseThrow(() -> new UnsatisfiedResolutionException(
                            format("No bean with name [%s] to satisfy attribute [%s]",
                                    handler.getOnPrepareFailureRef(), "onRedeliveryRef")));
            builder.setOnRedelivery(processor);
        }

        if (nonNull(handler.getRedeliveryPolicy())) {
            RedeliveryPolicyFactoryBean policy = handler.getRedeliveryPolicy();
            policy.setBeanManager(manager);
            builder.setRedeliveryPolicy(policy.getObject());
        }

        if (isNotEmpty(handler.getRedeliveryPolicyRef())) {
            RedeliveryPolicy policy = getReferenceByName(manager, handler.getRedeliveryPolicyRef(), RedeliveryPolicy.class)
                    .orElseThrow(() -> new UnsatisfiedResolutionException(
                            format("No bean with name [%s] to satisfy attribute [%s]",
                                    handler.getRedeliveryPolicyRef(), "redeliveryPolicyRef")));
            builder.setRedeliveryPolicy(policy);
        }
    }

}
