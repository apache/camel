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
package org.apache.camel.component.stax;

import org.apache.camel.Expression;

/**
 * Builder for StAX support.
 */
public final class StAXBuilder {

    private StAXBuilder() {
        // no-op
    }

    /**
     * Creates a {@link StAXJAXBIteratorExpression}.
     *
     * @param clazz the class which has JAXB annotations to bind POJO.
     */
    public static <T> Expression stax(Class<T> clazz) {
        return new StAXJAXBIteratorExpression<T>(clazz);
    }

    /**
     * Creates a {@link StAXJAXBIteratorExpression}.
     *
     * @param clazzName the FQN name of the class which has JAXB annotations to bind POJO.
     */
    public static <T> Expression stax(String clazzName) {
        return new StAXJAXBIteratorExpression<T>(clazzName);
    }

    /**
     * Creates a {@link org.apache.camel.component.stax.StAXJAXBIteratorExpression}.
     *
     * @param clazz            the class which has JAXB annotations to bind POJO.
     * @param isNamespaceAware sets the namespace awareness of the xml reader
     */
    public static <T> Expression stax(Class<T> clazz, boolean isNamespaceAware) {
        return new StAXJAXBIteratorExpression<T>(clazz, isNamespaceAware);
    }

    /**
     * Creates a {@link org.apache.camel.component.stax.StAXJAXBIteratorExpression}.
     *
     * @param clazzName        the FQN name of the class which has JAXB annotations to bind POJO.
     * @param isNamespaceAware sets the namespace awareness of the xml reader
     */
    public static <T> Expression stax(String clazzName, boolean isNamespaceAware) {
        return new StAXJAXBIteratorExpression<T>(clazzName, isNamespaceAware);
    }

}
