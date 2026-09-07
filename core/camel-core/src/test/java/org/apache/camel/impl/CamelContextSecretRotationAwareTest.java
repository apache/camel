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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.SecretRotationAware;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link SecretRotationAware} beans are notified when the context is reloaded, so they can re-authenticate
 * with a rotated secret before the routes are restarted.
 */
public class CamelContextSecretRotationAwareTest extends ContextTestSupport {

    private final MyRotationAware bean = new MyRotationAware();
    private final FailingRotationAware failing = new FailingRotationAware();
    private final List<String> order = new ArrayList<>();

    @Test
    public void testRegistryBeanIsNotified() throws Exception {
        assertThat(bean.getCounter()).isZero();

        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        assertThat(crs).isNotNull();
        crs.onReload("CamelContextSecretRotationAwareTest");

        assertThat(bean.getCounter()).isOne();
        assertThat(bean.getLastSource()).isEqualTo("CamelContextSecretRotationAwareTest");
    }

    @Test
    public void testNotifiedOnEveryReload() {
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        crs.onReload("first");
        crs.onReload("second");

        assertThat(bean.getCounter()).isEqualTo(2);
        assertThat(bean.getLastSource()).isEqualTo("second");
    }

    @Test
    public void testFailingListenerDoesNotBreakTheReload() {
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        crs.onReload("boom");

        // the failing listener was invoked, but the reload still succeeded and the other listener was notified
        assertThat(failing.getCounter()).isOne();
        assertThat(bean.getCounter()).isOne();
        assertThat(crs.getLastError()).isNull();
        assertThat(context.getRoutes()).hasSize(1);
    }

    @Test
    public void testNotifiedBeforeRoutesAreReloaded() {
        ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
        crs.onReload("ordering");

        // the secret must be refreshed before the routes come back up, otherwise the new consumers
        // would be created with the credentials that were just rotated away
        assertThat(order).containsExactly("secret-rotation", "route-reload");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = context.getPropertiesComponent();
        MySource my = new MySource();
        ServiceHelper.startService(my);
        pc.addPropertiesSource(my);

        context.getRegistry().bind("myRotationAware", bean);
        context.getRegistry().bind("failingRotationAware", failing);

        ContextReloadStrategy crs = new DefaultContextReloadStrategy() {
            @Override
            protected void notifySecretRotation(Object source) {
                order.add("secret-rotation");
                super.notifySecretRotation(source);
            }

            @Override
            protected void reloadRoutes(Object source) throws Exception {
                order.add("route-reload");
                super.reloadRoutes(source);
            }
        };
        context.addService(crs);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(constant("{{hello}}"))
                        .to("mock:result");
            }
        };
    }

    private static class MyRotationAware implements SecretRotationAware {

        private final AtomicInteger counter = new AtomicInteger();
        private volatile String lastSource;

        @Override
        public void onSecretRotation(Object source) {
            counter.incrementAndGet();
            lastSource = source != null ? source.toString() : null;
        }

        int getCounter() {
            return counter.get();
        }

        String getLastSource() {
            return lastSource;
        }
    }

    private static class FailingRotationAware implements SecretRotationAware {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public void onSecretRotation(Object source) {
            counter.incrementAndGet();
            throw new IllegalStateException("Cannot re-authenticate");
        }

        int getCounter() {
            return counter.get();
        }
    }

    private static class MySource extends ServiceSupport implements PropertiesSource {

        private int counter;

        @Override
        public String getName() {
            return "my";
        }

        @Override
        public String getProperty(String name) {
            if ("hello".equals(name)) {
                return "Hello " + counter;
            }
            return null;
        }

        @Override
        protected void doStart() {
            // the properties source will be restarted
            counter++;
        }
    }
}
