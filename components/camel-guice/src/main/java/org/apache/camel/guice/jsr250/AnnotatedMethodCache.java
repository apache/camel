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
package org.apache.camel.guice.jsr250;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A cache which maintains which method is annotated by a given annotation for
 * each class
 * 
 * @version
 */
class AnnotatedMethodCache {
    private final Class<? extends Annotation> annotationType;
    private Map<Class<?>, Method> methodCache = Collections
            .synchronizedMap(new WeakHashMap<Class<?>, Method>());

    AnnotatedMethodCache(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    /**
     * Looks up the method which is annotated for the given type
     */
    public Method getMethod(Class<?> type) {
        // if we are invoked concurrently it doesn't matter if we look up the
        // method
        // concurrently - its the same instance that will be overwritten in the
        // map
        Method method = methodCache.get(type);
        if (method == null) {
            method = findMethodWithAnnotation(type, annotationType);
            if (method != null) {
                if (method.getParameterTypes().length != 0) {
                    throw new IllegalArgumentException(
                            "Method should have no arguments for @PostConstruct "
                                    + method);
                }
                methodCache.put(type, method);
            }
        }
        return method;
    }

    protected Method findMethodWithAnnotation(Class<?> type,
            Class<? extends Annotation> annotationType) {
        Method[] methods = type.getDeclaredMethods();
        for (Method method : methods) {
            Annotation fromElement = method.getAnnotation(annotationType);
            if (fromElement != null) {
                return method;
            }
        }
        if (!Object.class.equals(type)) {
            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                return findMethodWithAnnotation(superclass, annotationType);
            }
        }
        return null;
    }
}
