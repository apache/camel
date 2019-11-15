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
package org.apache.camel.test.cdi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import static javax.interceptor.Interceptor.Priority.APPLICATION;

final class CamelCdiTestExtension implements Extension {

    private final Beans beans;

    CamelCdiTestExtension(Beans beans) {
        this.beans = beans;
    }

    /**
     * Activates the alternatives declared with {@code @Beans} globally for the
     * application.
     * <p/>
     * For every types and every methods of every types declared with
     * {@link Beans#alternatives()}, the {@code Priority} annotation is added
     * so that the corresponding alternatives are selected globally for the
     * entire application.
     *
     * @see Beans
     */
    private <T> void alternatives(@Observes @WithAnnotations(Alternative.class) ProcessAnnotatedType<T> pat) {
        AnnotatedType<T> type = pat.getAnnotatedType();

        if (!Arrays.asList(beans.alternatives()).contains(type.getJavaClass())) {
            // Only select globally the alternatives that are declared with @Beans
            return;
        }

        Set<AnnotatedMethod<? super T>> methods = new HashSet<>();
        for (AnnotatedMethod<? super T> method : type.getMethods()) {
            if (method.isAnnotationPresent(Alternative.class) && !method.isAnnotationPresent(Priority.class)) {
                methods.add(new AnnotatedMethodDecorator<>(method, PriorityLiteral.of(APPLICATION)));
            }
        }

        if (type.isAnnotationPresent(Alternative.class) && !type.isAnnotationPresent(Priority.class)) {
            pat.setAnnotatedType(new AnnotatedTypeDecorator<>(type, PriorityLiteral.of(APPLICATION), methods));
        } else if (!methods.isEmpty()) {
            pat.setAnnotatedType(new AnnotatedTypeDecorator<>(type, methods));
        }
    }
}
