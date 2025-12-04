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

package org.apache.camel.support;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A special registry which is used for local beans.
 *
 * This {@link org.apache.camel.spi.Registry} is only intended to be used by camel-core.
 */
public final class LocalBeanRegistry extends SupplierRegistry {

    private final Map<String, String> destroyMethods = new HashMap<>();

    public LocalBeanRegistry() {}

    public void registerDestroyMethod(String id, String method) {
        destroyMethods.put(id, method);
    }

    /**
     * Makes a copy of this registry
     */
    public LocalBeanRegistry copy() {
        LocalBeanRegistry copy = new LocalBeanRegistry();
        copy.putAll(this);
        return copy;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(keySet());
    }

    @Override
    public void close() throws IOException {
        // stop all beans that has destroy method
        destroyMethods.forEach((id, method) -> {
            Object bean = lookupByName(id);
            if (bean != null) {
                try {
                    org.apache.camel.support.ObjectHelper.invokeMethodSafe(method, bean);
                } catch (Exception e) {
                    // ignore
                }
            }
        });
        destroyMethods.clear();
        super.close();
    }
}
