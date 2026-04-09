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
package org.apache.camel.support;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.ContextServiceLoaderPluginResolver;
import org.apache.camel.spi.ContextServicePlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteWatcherReloadStrategyOnReloadTest extends ContextTestSupport {

    @Test
    public void testOnReloadInvokesPlugins() throws Exception {
        AtomicInteger reloadCount = new AtomicInteger();

        // Create a test plugin that tracks reload calls
        ContextServicePlugin testPlugin = new ContextServicePlugin() {
            @Override
            public void load(CamelContext camelContext) {
                // NO-OP
            }

            @Override
            public void onReload(CamelContext camelContext) {
                reloadCount.incrementAndGet();
            }
        };

        // Register a custom resolver with our test plugin
        ContextServiceLoaderPluginResolver resolver = new TestContextServiceLoaderPluginResolver(context, testPlugin);
        context.getCamelContextExtension().addContextPlugin(ContextServiceLoaderPluginResolver.class, resolver);

        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy();
        strategy.setCamelContext(context);

        // Trigger route reload
        strategy.onRouteReload(Collections.emptyList(), false);

        assertEquals(1, reloadCount.get(), "onReload should have been called once on the plugin");

        // Trigger another reload
        strategy.onRouteReload(Collections.emptyList(), false);

        assertEquals(2, reloadCount.get(), "onReload should have been called twice on the plugin");
    }

    @Test
    public void testOnReloadDefaultNoOp() throws Exception {
        // Verify the default onReload is a no-op and does not throw
        ContextServicePlugin plugin = new ContextServicePlugin() {
            @Override
            public void load(CamelContext camelContext) {
                // NO-OP
            }
        };

        // Should not throw
        plugin.onReload(context);
    }

    /**
     * A simple test resolver that delegates to a single plugin.
     */
    private static class TestContextServiceLoaderPluginResolver implements ContextServiceLoaderPluginResolver {
        private CamelContext camelContext;
        private final ContextServicePlugin plugin;

        TestContextServiceLoaderPluginResolver(CamelContext camelContext, ContextServicePlugin plugin) {
            this.camelContext = camelContext;
            this.plugin = plugin;
        }

        @Override
        public void onReload() {
            plugin.onReload(camelContext);
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void suspend() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public ServiceStatus getStatus() {
            return ServiceStatus.Started;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isStarting() {
            return false;
        }

        @Override
        public boolean isStopping() {
            return false;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public boolean isSuspending() {
            return false;
        }

        @Override
        public boolean isSuspended() {
            return false;
        }

        @Override
        public boolean isRunAllowed() {
            return true;
        }
    }
}
