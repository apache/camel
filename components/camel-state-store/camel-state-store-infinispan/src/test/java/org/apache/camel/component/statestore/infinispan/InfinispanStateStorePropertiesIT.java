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
package org.apache.camel.component.statestore.infinispan;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.statestore.StateStoreBackend;
import org.apache.camel.component.statestore.StateStoreConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.apache.camel.test.main.junit5.CamelMainTestSupport;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that an Infinispan backend can be configured via properties (camel.beans.*) and auto-discovered. The backend is
 * created via properties but the {@link RemoteCacheManager} is injected programmatically since it requires
 * authentication and platform-specific configuration that cannot be expressed as simple properties.
 */
class InfinispanStateStorePropertiesIT extends CamelMainTestSupport {

    private static final boolean IS_MAC_OS = System.getProperty("os.name", "").startsWith("Mac");

    @RegisterExtension
    static InfinispanService service = InfinispanServiceFactory.createSingletonInfinispanService();

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties props = new Properties();
        props.setProperty("camel.beans.infinispanBackend",
                "#class:" + InfinispanStateStoreBackend.class.getName());
        props.setProperty("camel.beans.infinispanBackend.cacheName", "state-store-props-test");
        return props;
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        super.bindToRegistry(registry);
        // Inject a pre-configured RemoteCacheManager into the property-created backend.
        // Authentication and platform-specific settings require programmatic configuration.
        InfinispanStateStoreBackend backend
                = registry.lookupByNameAndType("infinispanBackend", InfinispanStateStoreBackend.class);
        if (backend != null) {
            backend.setCacheManager(createCacheManager());
        }
    }

    private RemoteCacheManager createCacheManager() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.forceReturnValues(true);
        builder.addServers(service.host() + ":" + service.port());
        if (service.username() != null) {
            builder.security().authentication()
                    .username(service.username())
                    .password(service.password())
                    .saslMechanism("SCRAM-SHA-512")
                    .realm("default")
                    .serverName("infinispan");
        }
        if (IS_MAC_OS) {
            Properties properties = new Properties();
            properties.put("infinispan.client.hotrod.client_intelligence", "BASIC");
            builder.withProperties(properties);
        }
        return new RemoteCacheManager(builder.build(), true);
    }

    @Test
    void testBackendConfiguredViaProperties() {
        StateStoreBackend backend = context.getRegistry().lookupByNameAndType("infinispanBackend", StateStoreBackend.class);
        assertNotNull(backend);
        assertInstanceOf(InfinispanStateStoreBackend.class, backend);
        assertEquals("state-store-props-test", ((InfinispanStateStoreBackend) backend).getCacheName());
    }

    @Test
    void testAutoDiscoveryPutAndGet() {
        // No backend=#infinispanBackend in the URI — auto-discovery should find it
        Object previous = template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertNull(previous);

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // No backend=# reference — relies on auto-discovery
                from("direct:put").to("state-store:ispnStore?operation=put");
                from("direct:get").to("state-store:ispnStore?operation=get");
            }
        };
    }
}
