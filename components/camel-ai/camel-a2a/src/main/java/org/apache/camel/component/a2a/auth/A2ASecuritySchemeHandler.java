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
package org.apache.camel.component.a2a.auth;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.model.SecurityScheme;

/**
 * Pluggable handler for a specific A2A security scheme type. One handler covers both the producer side (applying
 * credentials to outgoing requests) and the consumer side (validating credentials from incoming requests) for a given
 * scheme, since both directions share the same protocol, header names, and token formats.
 * <p>
 * Implementations are discovered from the Camel registry via
 * {@code registry.findByType(A2ASecuritySchemeHandler.class)} at endpoint startup. The component ships default handlers
 * for the standard A2A scheme types: {@code http} (bearer), {@code apiKey}, {@code oauth2}, and {@code openIdConnect}.
 * Users can override any default by registering a custom handler bean with the same {@link #schemeType()}.
 *
 * @see SecurityScheme
 */
public interface A2ASecuritySchemeHandler {

    /**
     * The A2A security scheme type this handler covers. Must match the {@code type} field in {@link SecurityScheme}.
     * Standard values: {@code "http"}, {@code "apiKey"}, {@code "oauth2"}, {@code "openIdConnect"}.
     */
    String schemeType();

    /**
     * Producer side: apply this scheme's credentials to an outgoing request exchange.
     *
     * @param exchange the exchange to enrich with credentials
     * @param scheme   the security scheme declaration from the agent card
     * @param config   the endpoint configuration (provides credential values)
     * @param context  the Camel context (for SPI resolution, e.g., OAuth token exchange)
     */
    void applyCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config, CamelContext context);

    /**
     * Consumer side: validate credentials from an incoming request exchange.
     *
     * @param  exchange          the incoming request exchange
     * @param  scheme            the security scheme declaration from the agent's own card
     * @param  config            the endpoint configuration (provides expected credential values)
     * @return                   an authenticated user profile on success
     * @throws SecurityException if credentials are missing or invalid
     */
    A2AUserProfile validateCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config);
}
