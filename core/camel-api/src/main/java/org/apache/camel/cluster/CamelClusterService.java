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
package org.apache.camel.cluster;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Ordered;
import org.apache.camel.Service;
import org.apache.camel.spi.IdAware;

public interface CamelClusterService extends Service, CamelContextAware, IdAware, Ordered {

    @Override
    default int getOrder() {
        return Ordered.LOWEST;
    }

    /**
     * Get a view of the cluster bound to a namespace creating it if needed. Multiple
     * calls to this method with the same namespace should return the same instance.
     * The instance is automatically started the first time it is instantiated and
     * if the cluster service is ready.
     *
     * @param namespace the namespace the view refer to.
     * @return the view.
     * @throws Exception if the view can't be created.
     */
    CamelClusterView getView(String namespace) throws Exception;

    /**
     * Release a view if it has no references.
     *
     * @param view the view.
     * @throws Exception
     */
    void releaseView(CamelClusterView view) throws Exception;

    /**
     * Return the namespaces handled by this service.
     */
    Collection<String> getNamespaces();

    /**
     * Force start of the view associated to the give namespace.
     */
    void startView(String namespace) throws Exception;

    /**
     * Force stop of the view associated to the give namespace.
     */
    void stopView(String namespace) throws Exception;

    /**
     * Check if the service is the leader on the given namespace.
     *
     * @param namespace the namespace.
     * @return
     */
    boolean isLeader(String namespace);

    /**
     * Attributes associated to the service.
     */
    default Map<String, Object> getAttributes() {
        return Collections.emptyMap();
    }

    /**
     * Access the underlying concrete CamelClusterService implementation to
     * provide access to further features.
     *
     * @param clazz the proprietary class or interface of the underlying concrete CamelClusterService.
     * @return an instance of the underlying concrete CamelClusterService as the required type.
     */
    default <T extends CamelClusterService> T unwrap(Class<T> clazz) {
        if (CamelClusterService.class.isAssignableFrom(clazz)) {
            return clazz.cast(this);
        }

        throw new IllegalArgumentException(
            "Unable to unwrap this CamelClusterService type (" + getClass() + ") to the required type (" + clazz + ")"
        );
    }

    @FunctionalInterface
    interface Selector {
        /**
         * Select a specific CamelClusterService instance among a collection.
         */
        Optional<CamelClusterService> select(Collection<CamelClusterService> services);
    }
}
