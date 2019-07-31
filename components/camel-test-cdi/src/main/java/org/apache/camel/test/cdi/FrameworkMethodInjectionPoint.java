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

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;

final class FrameworkMethodInjectionPoint implements InjectionPoint {

    private final Method method;

    private final FrameworkAnnotatedParameter annotated;

    private final Type type;

    private final Set<Annotation> qualifiers;

    FrameworkMethodInjectionPoint(Method method, int position, BeanManager manager) {
        this.method = method;
        this.annotated = new FrameworkAnnotatedParameter(method, position, manager);
        this.type = method.getGenericParameterTypes()[position];
        this.qualifiers = new HashSet<>();
        for (Annotation annotation : method.getParameterAnnotations()[position]) {
            if (manager.isQualifier(annotation.annotationType())) {
                qualifiers.add(annotation);
            }
        }
    }

    @Override
    public Bean<?> getBean() {
        return null;
    }

    @Override
    public Member getMember() {
        return method;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Collections.unmodifiableSet(qualifiers);
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean isDelegate() {
        return false;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public Annotated getAnnotated() {
        return annotated;
    }
}
