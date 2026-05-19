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
package org.apache.camel;

import org.apache.camel.spi.HasCamelContext;

/**
 * Marker for objects that wish to receive the owning {@link CamelContext}.
 * <p/>
 * Camel inspects objects added to the context (components, processors, beans registered in the
 * {@link org.apache.camel.spi.Registry}, route policies, ...) and, when they implement this interface,
 * {@link #setCamelContext(CamelContext)} is called so they can keep a reference to their context. This is the standard
 * way for SPI implementations and user beans to reach into Camel APIs from inside a route.
 * <p/>
 * The static {@link #trySetCamelContext(Object, CamelContext)} helper is convenient for code paths that wire up
 * arbitrary objects without first knowing whether they need the context.
 *
 * @see CamelContext
 */
public interface CamelContextAware extends HasCamelContext {

    /**
     * Set the {@link CamelContext} context if the object is an instance of {@link CamelContextAware}.
     */
    static <T> T trySetCamelContext(T object, CamelContext camelContext) {
        if (camelContext != null && object instanceof CamelContextAware camelContextAware) {
            camelContextAware.setCamelContext(camelContext);
        }

        return object;
    }

    /**
     * Injects the {@link CamelContext}
     *
     * @param camelContext the Camel context
     */
    void setCamelContext(CamelContext camelContext);
}
