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
package org.apache.camel;

/**
 * An interface to represent an object which wishes to be injected with
 * a {@link Component}.
 */
public interface ComponentAware {

    /**
     * Injects the {@link Component}
     *
     * @param component the component
     */
    void setComponent(Component component);

    /**
     * Get the {@link Component}
     *
     * @return the component
     */
    Component getComponent();

    /**
     * Get the {@link Component} as the specified type.
     *
     * @param type the proprietary class or interface of the underlying concrete Component.
     * @return an instance of the underlying concrete Component as the required type.
     * @throws IllegalArgumentException if the component class can't be cast to required type,
     */
    default <T extends Component> T getComponent(Class<T> type) {
        final Component component = getComponent();

        if (component == null) {
            return null;
        }

        if (Component.class.isAssignableFrom(type)) {
            return type.cast(component);
        }

        throw new IllegalArgumentException(
            "Unable to unwrap the Component type (" + component.getClass() + ") to the required type (" + type + ")"
        );
    }
}
