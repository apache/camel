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

import org.jspecify.annotations.Nullable;

/**
 * Implemented by objects that want the {@link Resource} they originate from to be injected into them.
 * <p/>
 * When Camel creates an object from a resource (for example a route loaded from a file), it sets the originating
 * {@link Resource} on the object if it implements this interface, so the object can later refer back to where it came
 * from. The {@link #trySetResource(Object, Resource)} helper performs this injection conditionally.
 *
 * @see   Resource
 * @since 3.17
 */
public interface ResourceAware {

    /**
     * Set the {@link Resource} resource if the object is an instance of {@link ResourceAware}.
     */
    static <T> T trySetResource(T object, Resource resource) {
        if (resource != null && object instanceof ResourceAware resourceAware) {
            resourceAware.setResource(resource);
        }

        return object;
    }

    /**
     * Gets the {@link Resource}.
     */
    @Nullable
    Resource getResource();

    /**
     * Sets the {@link Resource}.
     */
    void setResource(Resource resource);
}
