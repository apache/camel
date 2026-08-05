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
package org.apache.camel.component.undertow;

import java.net.URI;

import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowEndpointTest {

    UndertowEndpoint endpoint;

    final URI withSlash = URI.create("http://0.0.0.0:8080/");

    final URI withoutSlash = URI.create("http://0.0.0.0:8080");

    @BeforeEach
    public void createEndpoint() {
        endpoint = new UndertowEndpoint(null, null);
    }

    @Test
    public void emptyPathShouldBeReplacedWithSlash() {
        endpoint.setHttpURI(withoutSlash);
        assertEquals(withSlash, endpoint.getHttpURI());
    }

    @Test
    public void nonEmptyPathShouldBeKeptSame() {
        endpoint.setHttpURI(withSlash);
        assertEquals(withSlash, endpoint.getHttpURI());
    }

    @Test
    void defaultHeaderFilterStrategyIsUndertowSpecific() {
        assertInstanceOf(UndertowHeaderFilterStrategy.class, endpoint.getHeaderFilterStrategy());
    }

    @Test
    void defaultBindingKeepsUndertowHeaderFilterStrategy() {
        // the endpoint pushes its own strategy into the lazily created binding, so the endpoint default
        // decides which strategy the binding ends up running
        DefaultUndertowHttpBinding binding
                = assertInstanceOf(DefaultUndertowHttpBinding.class, endpoint.getUndertowHttpBinding());
        HeaderFilterStrategy strategy = binding.getHeaderFilterStrategy();
        assertInstanceOf(UndertowHeaderFilterStrategy.class, strategy);

        // the undertow-specific prefixes added by CAMEL-23588 must therefore be in effect
        assertTrue(strategy.applyFilterToExternalHeaders(UndertowConstants.CONNECTION_KEY, "aValue", null));
        assertTrue(strategy.applyFilterToExternalHeaders(UndertowConstants.CONNECTION_KEY_LIST, "aValue", null));
        assertTrue(strategy.applyFilterToExternalHeaders(UndertowConstants.SEND_TO_ALL, "aValue", null));
        assertTrue(strategy.applyFilterToCamelHeaders(UndertowConstants.CONNECTION_KEY, "aValue", null));
    }

    @Test
    void explicitHeaderFilterStrategyIsHandedToTheBinding() {
        HeaderFilterStrategy custom = new HttpHeaderFilterStrategy();
        endpoint.setHeaderFilterStrategy(custom);

        DefaultUndertowHttpBinding binding
                = assertInstanceOf(DefaultUndertowHttpBinding.class, endpoint.getUndertowHttpBinding());
        assertSame(custom, binding.getHeaderFilterStrategy());
    }
}
