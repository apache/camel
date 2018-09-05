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
package org.apache.camel.cdi;

/**
 * The Camel CDI configuration. Camel CDI fires a {@code CdiCamelConfiguration} event
 * during the deployment phase that the application can observe and use to configure it.
 *
 * Note that the event fired can only be used within the observer method invocation context.
 * Any attempt to call one of its methods outside of that context will result in an 
 * `IllegalStateException` to be thrown.
 */
public interface CdiCamelConfiguration {

    /**
     * Overrides the Camel CDI behavior to automatically add all RouteBuilders to the corresponding Camel contexts.
     *
     * @return this Camel CDI configuration
     * @throws IllegalStateException if called outside of the observer method invocation
     */
    CdiCamelConfiguration autoConfigureRoutes(boolean autoConfigureRoutes);

    /**
     * @return Current state of autoConfigureRoutes parameter.
     */
    boolean autoConfigureRoutes();
    
    /**
     * Overrides the Camel CDI behavior to automatically start all Camel contexts.
     * @return this Camel CDI configuration
     * @throws IllegalStateException if called outside of the observer method invocation
     */
    CdiCamelConfiguration autoStartContexts(boolean autoStartContexts);

    /**
     * @return Current state of autoStartContexts parameter.
     */
    boolean autoStartContexts();
    
}