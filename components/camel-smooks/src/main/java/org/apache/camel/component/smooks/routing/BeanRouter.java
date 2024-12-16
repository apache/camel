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
package org.apache.camel.component.smooks.routing;

import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import javax.inject.Inject;

import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.smooks.SmooksConstants;
import org.smooks.api.ApplicationContext;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksConfigException;
import org.smooks.api.SmooksException;
import org.smooks.api.delivery.ordering.Consumer;
import org.smooks.api.lifecycle.PostExecutionLifecycle;
import org.smooks.api.lifecycle.PreExecutionLifecycle;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.api.resource.visitor.sax.ng.AfterVisitor;
import org.smooks.assertion.AssertArgument;
import org.smooks.cartridges.javabean.BeanMapExpressionEvaluator;
import org.smooks.engine.resource.config.DefaultResourceConfig;
import org.smooks.support.FreeMarkerTemplate;
import org.smooks.support.FreeMarkerUtils;

/**
 * Camel bean routing visitor.
 *
 */
public class BeanRouter implements AfterVisitor, Consumer, PreExecutionLifecycle, PostExecutionLifecycle {

    @Inject
    protected String beanId;

    @Inject
    protected String toEndpoint;

    @Inject
    protected Optional<String> condition;

    @Inject
    protected Optional<String> correlationIdName;

    @Inject
    protected Optional<FreeMarkerTemplate> correlationIdPattern;

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected ResourceConfig resourceConfig;

    protected ProducerTemplate producerTemplate;
    protected BeanRouterObserver camelRouterObservable;
    protected CamelContext camelContext;

    public BeanRouter() {
    }

    public BeanRouter(final CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @PostConstruct
    public void postConstruct() {
        if (resourceConfig == null) {
            resourceConfig = new DefaultResourceConfig();
        }

        producerTemplate = getCamelContext().createProducerTemplate();
        if (isBeanRoutingConfigured()) {
            camelRouterObservable = new BeanRouterObserver(this, beanId);
            if (condition != null && condition.isPresent()) {
                camelRouterObservable.setConditionEvaluator(new BeanMapExpressionEvaluator(condition.get()));
            }
        }

        if ((correlationIdName != null && correlationIdName.isPresent())
                && (correlationIdPattern == null || correlationIdPattern.isEmpty())) {
            throw new SmooksConfigException(
                    "Camel router component configured with a 'correlationIdName', but 'correlationIdPattern' is not configured.");
        }
        if ((correlationIdName == null || correlationIdName.isEmpty())
                && (correlationIdPattern != null && correlationIdPattern.isPresent())) {
            throw new SmooksConfigException(
                    "Camel router component configured with a 'correlationIdPattern', but 'correlationIdName' is not configured.");
        }
    }

    /**
     * Set the beanId of the bean to be routed.
     *
     * @param  beanId the beanId to set
     * @return        This router instance.
     */
    public BeanRouter setBeanId(final String beanId) {
        this.beanId = beanId;
        return this;
    }

    /**
     * Set the Camel endpoint to which the bean is to be routed.
     *
     * @param  toEndpoint the toEndpoint to set
     * @return            This router instance.
     */
    public BeanRouter setToEndpoint(final String toEndpoint) {
        this.toEndpoint = toEndpoint;
        return this;
    }

    /**
     * Set the correlationId header name.
     *
     * @return This router instance.
     */
    public BeanRouter setCorrelationIdName(String correlationIdName) {
        AssertArgument.isNotNullAndNotEmpty(correlationIdName, "correlationIdName");
        this.correlationIdName = Optional.of(correlationIdName);
        return this;
    }

    /**
     * Set the correlationId pattern used to generate correlationIds.
     *
     * @param  correlationIdPattern The pattern generator template.
     * @return                      This router instance.
     */
    public BeanRouter setCorrelationIdPattern(final String correlationIdPattern) {
        this.correlationIdPattern = Optional.of(new FreeMarkerTemplate(correlationIdPattern));
        return this;
    }

    @Override
    public void visitAfter(final Element element, final ExecutionContext executionContext) throws SmooksException {
        final Object bean = getBeanFromExecutionContext(executionContext, beanId);
        sendBean(bean, executionContext);
    }

    /**
     * Send the bean to the target endpoint.
     *
     * @param bean             The bean to be sent.
     * @param executionContext The execution context.
     */
    protected void sendBean(final Object bean, final ExecutionContext executionContext) {
        try {
            if (correlationIdPattern != null && correlationIdPattern.isPresent()) {
                Processor processor = exchange -> {
                    Message in = exchange.getIn();
                    in.setBody(bean);
                    in.setHeader(correlationIdName.orElse(null),
                            correlationIdPattern.get().apply(FreeMarkerUtils.getMergedModel(executionContext)));
                };
                producerTemplate.send(toEndpoint, processor);
            } else {
                producerTemplate.sendBodyAndHeaders(toEndpoint, bean,
                        Map.of(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, executionContext));
            }
        } catch (final Exception e) {
            throw new SmooksException(String.format("Exception routing beanId [%s] to endpoint [%s]", beanId, toEndpoint), e);
        }
    }

    protected Object getBeanFromExecutionContext(final ExecutionContext executionContext, final String beanId) {
        final Object bean = executionContext.getBeanContext().getBean(beanId);
        if (bean == null) {
            throw new SmooksException(
                    String.format("Exception routing beanId [%s]. The bean was not found in the Smooks execution context",
                            beanId));
        }

        return bean;
    }

    protected CamelContext getCamelContext() {
        if (camelContext == null) {
            return applicationContext.getRegistry().lookup(CamelContext.class);
        } else {
            return camelContext;
        }
    }

    protected boolean isBeanRoutingConfigured() {
        return "none".equals(resourceConfig.getSelectorPath().getSelector());
    }

    @PreDestroy
    public void preDestroy() {
        try {
            producerTemplate.stop();
        } catch (final Exception e) {
            throw new SmooksException(e.getMessage(), e);
        }
    }

    @Override
    public boolean consumes(final Object object) {
        return beanId.equals(object);
    }

    @Override
    public void onPostExecution(ExecutionContext executionContext) {
        if (isBeanRoutingConfigured()) {
            executionContext.getBeanContext().removeObserver(camelRouterObservable);
        }
    }

    @Override
    public void onPreExecution(ExecutionContext executionContext) {
        if (isBeanRoutingConfigured()) {
            executionContext.getBeanContext().addObserver(camelRouterObservable);
        }
    }
}
