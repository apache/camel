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
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * A stub that stands in for another component: {@code camel run --stub} serves a URI such as {@code kafka:refunds} with
 * the stub component, a queue in memory, so the integration runs without the system behind it. Chaos counts such an
 * endpoint as the component it stands in for: remote when that component is remote (from the JSON of the component,
 * found when its JAR is on the classpath, as {@code camel run} downloads it for a stub too), and with the options of
 * its URI, which the stub component does not configure. The stub component is known by its class name, as camel-core
 * knows it, so camel-chaos does not depend on camel-stub.
 */
final class ChaosStubs {

    private static final String STUB_COMPONENT = "StubComponent";

    private ChaosStubs() {
    }

    /**
     * The scheme of the component a stub stands in for ({@code kafka} for {@code kafka://refunds}), or null when the
     * endpoint is not a stub, or is a plain {@code stub:} endpoint.
     */
    static String stubbedScheme(Endpoint endpoint) {
        Component component = endpoint != null ? endpoint.getComponent() : null;
        // NOTE: the stub component is not available at compilation time
        if (component == null || !STUB_COMPONENT.equals(component.getClass().getSimpleName())) { // NOSONAR
            return null;
        }
        String scheme = ChaosPatterns.scheme(endpoint.getEndpointUri());
        return "stub".equals(scheme) ? null : scheme;
    }

    /** Whether the endpoint is remote: a stub is when the component it stands in for is. */
    static boolean isRemote(Endpoint endpoint) {
        if (endpoint.isRemote()) {
            return true;
        }
        String scheme = stubbedScheme(endpoint);
        return scheme != null && isRemoteComponent(endpoint.getCamelContext(), scheme);
    }

    /** Whether the component of the scheme is remote, from its JSON; false when it cannot be found. */
    static boolean isRemoteComponent(CamelContext context, String scheme) {
        if (!(context instanceof CatalogCamelContext catalog)) {
            return false;
        }
        try {
            String json = catalog.getComponentParameterJsonSchema(scheme);
            if (json == null) {
                return false;
            }
            JsonObject component = ((JsonObject) Jsoner.deserialize(json)).getMap("component");
            return component != null && component.getBooleanOrDefault("remote", false);
        } catch (Exception e) {
            // the JSON of a component that cannot be read: the stub is not taken as remote
            return false;
        }
    }

    /** The options in the URI of a stub, by name in any case; empty when the URI cannot be read. */
    static Map<String, Object> uriOptions(Endpoint endpoint) {
        Map<String, Object> answer = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String query = URISupport.extractQuery(endpoint.getEndpointUri());
        if (query != null) {
            try {
                answer.putAll(URISupport.parseQuery(query));
            } catch (Exception e) {
                // a query that cannot be read has no options
            }
        }
        return answer;
    }
}
