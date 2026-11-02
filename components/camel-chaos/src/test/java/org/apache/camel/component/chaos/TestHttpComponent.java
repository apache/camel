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
package org.apache.camel.component.chaos;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;

/**
 * A component for the tests that stands for an HTTP component (registered as {@code https}): its endpoints have the
 * options {@code throwExceptionOnFailure} (default true) and {@code okStatusCodeRange} (default 200-299), which its
 * configurer returns as the generated configurers of camel-http and camel-vertx-http do ({@code getOptionValue} returns
 * the boolean and the String of the endpoint). Its producer counts the sends that reached it.
 */
final class TestHttpComponent extends DefaultComponent {

    final AtomicInteger sends = new AtomicInteger();
    private final PropertyConfigurer configurer = new EndpointConfigurer();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
        TestHttpEndpoint endpoint = new TestHttpEndpoint(uri, this);
        Object throwOnFailure = parameters.remove("throwExceptionOnFailure");
        if (throwOnFailure != null) {
            endpoint.throwExceptionOnFailure = Boolean.parseBoolean(throwOnFailure.toString());
        }
        Object okRange = parameters.remove("okStatusCodeRange");
        if (okRange != null) {
            endpoint.okStatusCodeRange = okRange.toString();
        }
        return endpoint;
    }

    @Override
    public PropertyConfigurer getEndpointPropertyConfigurer() {
        return configurer;
    }

    static final class TestHttpEndpoint extends DefaultEndpoint {

        boolean throwExceptionOnFailure = true;
        String okStatusCodeRange = "200-299";

        TestHttpEndpoint(String uri, TestHttpComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    ((TestHttpComponent) getEndpoint().getComponent()).sends.incrementAndGet();
                    exchange.getMessage().setBody("real response");
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException("No consumer");
        }
    }

    private static final class EndpointConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if ("throwExceptionOnFailure".equalsIgnoreCase(name)) {
                ((TestHttpEndpoint) target).throwExceptionOnFailure = Boolean.parseBoolean(value.toString());
                return true;
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return "throwExceptionOnFailure".equalsIgnoreCase(name) ? boolean.class : null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if ("throwExceptionOnFailure".equalsIgnoreCase(name)) {
                return ((TestHttpEndpoint) target).throwExceptionOnFailure;
            }
            if ("okStatusCodeRange".equalsIgnoreCase(name)) {
                return ((TestHttpEndpoint) target).okStatusCodeRange;
            }
            return null;
        }
    }
}
