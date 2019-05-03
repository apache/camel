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
package org.apache.camel.spi;

import org.apache.camel.Endpoint;

/**
 * Endpoint strategy with callback invoked when an {@link org.apache.camel.Endpoint} is about to be registered to the
 * endpoint registry in {@link org.apache.camel.CamelContext}. This callback allows you to intervene
 * and return a mixed in {@link org.apache.camel.Endpoint}.
 * <p/>
 * The InterceptSendToEndpointDefinition uses this to allow it to proxy
 * endpoints so it can intercept sending to the given endpoint.
 */
public interface EndpointStrategy {

    /**
     * Register the endpoint.
     *
     * @param uri  uri of endpoint
     * @param endpoint the current endpoint to register
     * @return the real endpoint to register, for instance a wrapped/enhanced endpoint.
     */
    Endpoint registerEndpoint(String uri, Endpoint endpoint);

}
