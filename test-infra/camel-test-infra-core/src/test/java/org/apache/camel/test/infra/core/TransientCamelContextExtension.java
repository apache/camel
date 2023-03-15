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
package org.apache.camel.test.infra.core;

import java.util.Objects;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transient Camel context extension that is recreated after every test, and is suitable for most of the simple use
 * cases in Camel and end-user applications.
 */
public class TransientCamelContextExtension extends AbstractCamelContextExtension {
    private static final Logger LOG = LoggerFactory.getLogger(TransientCamelContextExtension.class);
    private final ContextLifeCycleManager lifeCycleManager;
    private final AnnotationProcessor fixtureProcessor;

    private CamelContext context;
    private ProducerTemplate producerTemplate;
    private ConsumerTemplate consumerTemplate;

    private static class TransientLifeCycleManager extends DefaultContextLifeCycleManager {
        @Override
        public void afterEach(CamelContext context) {
            super.afterEach(context);

            context.shutdown();
        }
    }

    /**
     * Creates a new instance of the extension
     */
    public TransientCamelContextExtension() {
        this(new TransientLifeCycleManager());
    }

    /**
     * Creates a new instance of the extension with a custom {@link ContextLifeCycleManager}
     *
     * @param lifeCycleManager a life cycle manager for the context
     */
    public TransientCamelContextExtension(ContextLifeCycleManager lifeCycleManager) {
        this.lifeCycleManager = lifeCycleManager;
        this.fixtureProcessor = new DefaultAnnotationProcessor(this);

        LOG.warn("Creating a new extension: {}", this);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        lifeCycleManager.afterAll(context);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        recreateContext(extensionContext);
    }

    private void recreateContext(ExtensionContext extensionContext) {
        context = createCamelContext(fixtureProcessor, extensionContext);
        Objects.requireNonNull(context, "Cannot run the test because the context is null");

        producerTemplate = context.createProducerTemplate();
        producerTemplate.start();

        consumerTemplate = context.createConsumerTemplate();
        consumerTemplate.start();

        lifeCycleManager.beforeAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        final Object o = extensionContext.getTestInstance().get();

        LOG.info("********************************************************************************");
        LOG.info("Testing: {} ({})", extensionContext.getDisplayName(), o.getClass().getName());

        fixtureProcessor.evalField(extensionContext, Produce.class, o, context);
        fixtureProcessor.evalField(extensionContext, EndpointInject.class, o, context);
        fixtureProcessor.evalField(extensionContext, BindToRegistry.class, o, context);

        if (!context.isStarted()) {
            fixtureProcessor.evalMethod(extensionContext, ContextFixture.class, o, context);
            fixtureProcessor.evalMethod(extensionContext, RouteFixture.class, o, context);

            setupBeanPostProcessors(context, o);

            lifeCycleManager.beforeEach(context);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        lifeCycleManager.afterEach(context);

        final Object o = extensionContext.getTestInstance().get();
        LOG.info("Testing done: {} ({})", extensionContext.getDisplayName(), o.getClass().getName());
        LOG.info("********************************************************************************");

        recreateContext(extensionContext);
    }

    @Override
    public CamelContext getContext() {
        return context;
    }

    @Override
    public ProducerTemplate getProducerTemplate() {
        return producerTemplate;
    }

    @Override
    public ConsumerTemplate getConsumerTemplate() {
        return consumerTemplate;
    }

    @Override
    public MockEndpoint getMockEndpoint(String uri) {
        MockEndpoint mock = getMockEndpoint(uri, true);

        return mock;
    }

    @Override
    public MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        return MockUtils.getMockEndpoint(context, uri, create);
    }
}
