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
package org.apache.camel.cloud;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Ordered;
import org.apache.camel.Service;
import org.apache.camel.spi.IdAware;

public interface ServiceRegistry extends Service, CamelContextAware, IdAware, Ordered {

    @Override
    default int getOrder() {
        return Ordered.LOWEST;
    }

    /**
     * Attributes associated to the service.
     */
    default Map<String, Object> getAttributes() {
        return Collections.emptyMap();
    }

    /**
     * Register the service definition.
     *
     * @param definition the service definition
     */
    void register(ServiceDefinition definition);


    /**
     * Remove the service definition.
     *
     * @param definition the service definition
     */
    void deregister(ServiceDefinition definition);

    /**
     * A selector used to pick up a service among a list.
     */
    @FunctionalInterface
    interface Selector {
        /**
         * Select a specific ServiceRegistry instance among a collection.
         */
        Optional<ServiceRegistry> select(Collection<ServiceRegistry> services);
    }
}
