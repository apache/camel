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
package org.apache.camel.component.platform.http.spi;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;

/**
 * Security handler for platform-http consumers.
 * <p/>
 * Platform HTTP engines use this handler to wrap the Camel route processor. Authentication is enforced before route
 * processing; engine-specific request parsing may already have happened.
 *
 * @since 4.21
 */
public interface PlatformHttpSecurityHandler {

    /**
     * Wraps the Camel route processor with this security handler.
     *
     * @param  endpoint  the platform-http endpoint
     * @param  processor the original route processor
     * @return           the secured processor
     */
    Processor wrapProcessor(PlatformHttpEndpoint endpoint, Processor processor);

    /**
     * Authenticates a prepared exchange without invoking the route processor.
     * <p/>
     * Engines that can run security before expensive request parsing can call this method and continue request handling
     * only when it returns {@code true}. The default implementation preserves compatibility with handlers that only
     * implement {@link #wrapProcessor(PlatformHttpEndpoint, Processor)}.
     *
     * @param  endpoint  the platform-http endpoint
     * @param  exchange  the exchange containing request metadata needed by the handler
     * @return           {@code true} when request processing may continue
     * @throws Exception if authentication fails due to an infrastructure error not represented on the exchange
     * @since            4.21
     */
    default boolean authenticate(PlatformHttpEndpoint endpoint, Exchange exchange) throws Exception {
        boolean[] routeAllowed = new boolean[1];
        wrapProcessor(endpoint, ignored -> routeAllowed[0] = true).process(exchange);
        return routeAllowed[0] && !exchange.isRouteStop();
    }
}
