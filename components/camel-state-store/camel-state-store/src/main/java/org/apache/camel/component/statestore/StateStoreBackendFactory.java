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
package org.apache.camel.component.statestore;

import org.apache.camel.spi.KeyValueRepository;

/**
 * Creates the {@link KeyValueRepository} backend for a given state store name.
 * <p>
 * Use this when each store name should map to its own backend instance, for example a separate cache per store. The
 * returned backend is owned by the {@link StateStoreComponent}, which starts and stops it.
 *
 * @since 4.24
 */
@FunctionalInterface
public interface StateStoreBackendFactory {

    /**
     * Creates the backend for the given store name.
     *
     * @param  storeName the name of the state store, as used in the endpoint URI
     * @return           the backend for the store, or {@code null} to fall back to the default backend resolution
     */
    KeyValueRepository createBackend(String storeName);
}
