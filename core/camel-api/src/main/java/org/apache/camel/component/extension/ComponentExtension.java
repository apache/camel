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
package org.apache.camel.component.extension;

/**
 * Marker interface for component extensions.
 * <p/>
 * An extension is a feature provided by the component such as ComponentVerifier.
 */
public interface ComponentExtension {

    /**
     * Access the underlying concrete ComponentExtension implementation to
     * provide access to further features.
     *
     * @param clazz the proprietary class or interface of the underlying concrete ComponentExtension.
     * @return an instance of the underlying concrete ComponentExtension as the required type.
     */
    default <T extends ComponentExtension> T unwrap(Class<T> clazz) {
        if (ComponentExtension.class.isAssignableFrom(clazz)) {
            return clazz.cast(this);
        }

        throw new IllegalArgumentException(
            "Unable to unwrap this ComponentExtension type (" + getClass() + ") to the required type (" + clazz + ")"
        );
    }
}
