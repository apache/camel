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

import org.apache.camel.Endpoint;
import org.apache.camel.spi.ContentCacheAware;
import org.apache.camel.support.DefaultComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MainDevModeContentCacheTest {

    @Test
    public void shouldDisableContentCacheWhenRoutesReloadEnabled() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        main.bind("dummy", new TestContentCacheComponent());

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertEquals(Boolean.FALSE, c.getContentCache(),
                    "contentCache should be auto-disabled when routesReloadEnabled=true");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldLeaveContentCacheUnsetWhenRoutesReloadDisabled() {
        Main main = new Main();
        // routesReloadEnabled defaults to false
        main.bind("dummy", new TestContentCacheComponent());

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertNull(c.getContentCache(),
                    "contentCache should remain unset when routesReloadEnabled is not active");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldRespectExplicitContentCacheTrue() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        TestContentCacheComponent component = new TestContentCacheComponent();
        component.setContentCache(Boolean.TRUE);
        main.bind("dummy", component);

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertEquals(Boolean.TRUE, c.getContentCache(),
                    "explicit user setting must not be overridden by dev-mode auto-flip");
        } finally {
            main.stop();
        }
    }

    @Test
    public void shouldRespectExplicitContentCacheFalse() {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true);
        TestContentCacheComponent component = new TestContentCacheComponent();
        component.setContentCache(Boolean.FALSE);
        main.bind("dummy", component);

        main.start();
        try {
            TestContentCacheComponent c = main.getCamelContext().getComponent("dummy", TestContentCacheComponent.class);
            assertEquals(Boolean.FALSE, c.getContentCache());
        } finally {
            main.stop();
        }
    }

    public static final class TestContentCacheComponent extends DefaultComponent implements ContentCacheAware {

        private Boolean contentCache;

        @Override
        public Boolean getContentCache() {
            return contentCache;
        }

        @Override
        public void setContentCache(Boolean contentCache) {
            this.contentCache = contentCache;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return null;
        }
    }
}
