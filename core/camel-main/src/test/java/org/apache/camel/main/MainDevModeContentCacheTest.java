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
package org.apache.camel.main;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainDevModeContentCacheTest {

    @Test
    public void shouldDisableContentCacheWhenRoutesReloadEnabled() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        main.bind("dummy", new TestContentCacheComponent());

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertFalse(c.isContentCache(),
                    "contentCache should be auto-disabled when routesReloadEnabled=true");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldKeepContentCacheEnabledWhenRoutesReloadDisabled() {
        Main main = new Main();
        // routesReloadEnabled defaults to false
        main.bind("dummy", new TestContentCacheComponent());

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertTrue(c.isContentCache(),
                    "contentCache should remain at its default (true) when routesReloadEnabled is not active");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldRespectExplicitContentCacheFalse() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        TestContentCacheComponent component = new TestContentCacheComponent();
        component.setContentCache(false);
        main.bind("dummy", component);

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertFalse(c.isContentCache(),
                    "explicit user setting (false) must not be touched by dev-mode auto-flip");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldHonorMainPropertyOverride() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        main.addInitialProperty("camel.component.dummy.contentCache", "true");
        main.bind("dummy", new TestContentCacheComponent());

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertTrue(c.isContentCache(),
                    "camel.component.<name>.contentCache=true must override the dev-mode auto-flip");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldRespectExplicitContentCacheOnUri() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        main.bind("dummy", new TestContentCacheComponent());

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertFalse(c.isContentCache(), "component-level contentCache should be auto-disabled");

            TestContentCacheEndpoint endpoint
                    = (TestContentCacheEndpoint) main.getCamelContext().getEndpoint("dummy:foo?contentCache=true");
            assertTrue(endpoint.isContentCache(),
                    "explicit contentCache=true on URI must override the component-level auto-flip");
        } finally {
            main.stop();
        }
    }

    static final class TestContentCacheComponent extends DefaultComponent {

        private boolean contentCache = true;

        public boolean isContentCache() {
            return contentCache;
        }

        public void setContentCache(boolean contentCache) {
            this.contentCache = contentCache;
        }

        @Override
        public PropertyConfigurer getComponentPropertyConfigurer() {
            return new TestContentCacheComponentConfigurer();
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            TestContentCacheEndpoint endpoint = new TestContentCacheEndpoint(uri, this);
            endpoint.setContentCache(contentCache);
            setProperties(endpoint, parameters);
            return endpoint;
        }
    }

    static final class TestContentCacheComponentConfigurer
            extends PropertyConfigurerSupport
            implements PropertyConfigurer, PropertyConfigurerGetter {

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if ("contentcache".equalsIgnoreCase(name)) {
                ((TestContentCacheComponent) target).setContentCache(property(camelContext, boolean.class, value));
                return true;
            }
            return false;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if ("contentcache".equalsIgnoreCase(name)) {
                return ((TestContentCacheComponent) target).isContentCache();
            }
            return null;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return "contentcache".equalsIgnoreCase(name) ? boolean.class : null;
        }
    }

    static final class TestContentCacheEndpoint extends DefaultEndpoint {

        private boolean contentCache;

        TestContentCacheEndpoint(String uri, TestContentCacheComponent component) {
            super(uri, component);
        }

        public boolean isContentCache() {
            return contentCache;
        }

        public void setContentCache(boolean contentCache) {
            this.contentCache = contentCache;
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}
