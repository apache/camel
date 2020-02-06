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
package org.apache.camel.component.webhook.support;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.webhook.WebhookCapableEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.support.DefaultEndpoint;

/**
 * A test endpoint for testing webhook capabilities
 */
public class TestEndpoint extends DefaultEndpoint implements WebhookCapableEndpoint {

    private static final List<String> DEFAULT_METHOD = Collections.unmodifiableList(Collections.singletonList("POST"));

    private Function<Processor, Processor> webhookHandler;

    private Runnable register;

    private Runnable unregister;

    private Supplier<List<String>> methods;

    private Supplier<Producer> producer;

    private Function<Processor, Consumer> consumer;

    private WebhookConfiguration webhookConfiguration;

    private boolean singleton;

    /**
     * For query parameter testing 1
     */
    private String foo;

    /**
     * For query parameter testing 2
     */
    private String bar;

    public TestEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Processor createWebhookHandler(Processor next) {
        if (this.webhookHandler != null) {
            return this.webhookHandler.apply(next);
        }
        return next;
    }

    @Override
    public void registerWebhook() {
        if (this.register != null) {
            this.register.run();
        }
    }

    @Override
    public void unregisterWebhook() {
        if (this.unregister != null) {
            this.unregister.run();
        }
    }

    @Override
    public void setWebhookConfiguration(WebhookConfiguration webhookConfiguration) {
        this.webhookConfiguration = webhookConfiguration;
    }

    public WebhookConfiguration getWebhookConfiguration() {
        return webhookConfiguration;
    }

    @Override
    public List<String> getWebhookMethods() {
        return this.methods != null ? this.methods.get() : DEFAULT_METHOD;
    }

    @Override
    public Producer createProducer() throws Exception {
        return this.producer != null ? this.producer.get() : null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return this.consumer != null ? this.consumer.apply(processor) : null;
    }

    @Override
    public boolean isSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public void setWebhookHandler(Function<Processor, Processor> webhookHandler) {
        this.webhookHandler = webhookHandler;
    }

    public void setRegisterWebhook(Runnable register) {
        this.register = register;
    }

    public void setUnregisterWebhook(Runnable unregister) {
        this.unregister = unregister;
    }

    public void setWebhookMethods(Supplier<List<String>> methods) {
        this.methods = methods;
    }

    public void setWebhookProducer(Supplier<Producer> producer) {
        this.producer = producer;
    }

    public void setConsumer(Function<Processor, Consumer> consumer) {
        this.consumer = consumer;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
}
