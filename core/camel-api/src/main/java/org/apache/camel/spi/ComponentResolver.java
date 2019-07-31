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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;

/**
 * Represents a resolver of components from a URI to be able to auto-load them using some
 * discovery mechanism like {@link org.apache.camel.impl.DefaultComponentResolver}
 */
public interface ComponentResolver {
    
    /**
     * Attempts to resolve the component for the given URI
     *
     * @param name the component name to resolve
     * @param context the context to load the component if it can be resolved
     * @return the component which is added to the context or null if it cannot be resolved
     * @throws Exception is thrown if the component could not be loaded
     */
    Component resolveComponent(String name, CamelContext context) throws Exception;
}
