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
package org.apache.camel.component.bean;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.util.ObjectHelper;

/**
 * This class aims at retaining the right methods while parsing a given
 * {@link java.lang.Class}.
 */
class MethodsFilter {

    private final List<Method> methods = new ArrayList<Method>();
    private final Class<?> inheritingClass;

    /**
     * Creates a <code>MethodsFilter</code> for a given {@link java.lang.Class}.
     *
     * @param clazz The {@link java.lang.Class} whose methods are to be
     *            filtered.
     */
    public MethodsFilter(Class<?> clazz) {
        this.inheritingClass = clazz;
    }

    /**
     * Retains methods, preferring those from public classes in case of
     * overrides.
     *
     * @param proposedMethod The method proposed to the filter.
     */
    void filterMethod(Method proposedMethod) {
        if (proposedMethod.isBridge()) {
            return;
        }

        for (int i = 0; i < methods.size(); i++) {
            Method alreadyRegistered = methods.get(i);

            if (Modifier.isPublic(proposedMethod.getDeclaringClass().getModifiers())) {
                boolean overridden = ObjectHelper.isOverridingMethod(inheritingClass, proposedMethod, alreadyRegistered, false);
                boolean overridding = ObjectHelper.isOverridingMethod(inheritingClass, alreadyRegistered, proposedMethod, false);

                boolean registeredMethodIsPublic = Modifier.isPublic(alreadyRegistered.getDeclaringClass().getModifiers());

                if (overridden && !registeredMethodIsPublic) {
                    // Retain the overridden method from a public class
                    methods.set(i, proposedMethod);
                    return;
                } else if (overridding) {
                    // Retain the override from a public class
                    methods.set(i, proposedMethod);
                    return;
                }
            }
        }

        methods.add(proposedMethod);
    }

    List<Method> asReadOnlyList() {
        return Collections.unmodifiableList(methods);
    }
}
