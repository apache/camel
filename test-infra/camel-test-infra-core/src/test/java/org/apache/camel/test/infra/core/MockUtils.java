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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.URISupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class MockUtils {
    private MockUtils() {

    }

    /**
     * Resolves an endpoint and asserts that it is found.
     */
    public static Endpoint resolveMandatoryEndpoint(CamelContext context, String endpointUri) {
        Endpoint endpoint = context.getEndpoint(endpointUri);

        assertNotNull(endpoint, "No endpoint found for URI: " + endpointUri);

        return endpoint;
    }

    /**
     * Resolves an endpoint and asserts that it is found.
     */
    public static <
            T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String endpointUri, Class<T> endpointType) {
        T endpoint = context.getEndpoint(endpointUri, endpointType);

        assertNotNull(endpoint, "No endpoint found for URI: " + endpointUri);

        return endpoint;
    }

    /**
     * Gets a mock endpoint for the given URI
     *
     * @param  context                 the {@link CamelContext} to use
     * @param  uri                     the URI to use to create or resolve an endpoint
     * @param  create                  whether to create the endpoint if it does not
     *
     * @return                         the mock endpoint
     * @throws NoSuchEndpointException if the endpoint does not exist and create is false
     */
    public static MockEndpoint getMockEndpoint(CamelContext context, String uri, boolean create)
            throws NoSuchEndpointException {
        // look for existing mock endpoints that have the same queue name, and
        // to
        // do that we need to normalize uri and strip out query parameters and
        // whatnot
        String n;
        try {
            n = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        // strip query
        int idx = n.indexOf('?');
        if (idx != -1) {
            n = n.substring(0, idx);
        }
        final String target = n;

        // lookup endpoints in registry and try to find it
        MockEndpoint found = (MockEndpoint) context.getEndpointRegistry().values().stream()
                .filter(e -> e instanceof MockEndpoint).filter(e -> {
                    String t = e.getEndpointUri();
                    // strip query
                    int idx2 = t.indexOf('?');
                    if (idx2 != -1) {
                        t = t.substring(0, idx2);
                    }
                    return t.equals(target);
                }).findFirst().orElse(null);

        if (found != null) {
            return found;
        }

        if (create) {
            return resolveMandatoryEndpoint(context, uri, MockEndpoint.class);
        } else {
            throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
        }
    }
}
