/**
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
package org.apache.camel.builder;

import org.apache.camel.impl.InterceptSendToMockEndpointStrategy;

/**
 * A {@link RouteBuilder} which has extended features when using
 * {@link org.apache.camel.model.RouteDefinition#adviceWith(org.apache.camel.CamelContext, RouteBuilder) adviceWith}.
 *
 * @version $Revision$
 */
public abstract class AdviceWithRouteBuilder extends RouteBuilder {

    /**
     * Mock all endpoints in the route.
     */
    public void mockEndpoints() throws Exception {
        getContext().removeEndpoints("*");
        getContext().addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(null));
    }

    /**
     * Mock all endpoints matching the given pattern.
     *
     * @param pattern  the pattern.
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(String, String)
     */
    public void mockEndpoints(String pattern) throws Exception {
        getContext().removeEndpoints(pattern);
        getContext().addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern));
    }

}
