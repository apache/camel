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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;

final class BeanDelegate<T> implements Bean<T> {

    private final Bean<T> delegate;

    private final Set<Annotation> qualifiers;

    @SafeVarargs
    BeanDelegate(Bean<T> delegate, Set<? extends Annotation>... sets) {
        this.delegate = delegate;
        this.qualifiers = new HashSet<>();
        for (Set<? extends Annotation> set : sets) {
            this.qualifiers.addAll(set);
        }
    }

    @Override
    public Set<Type> getTypes() {
        return delegate.getTypes();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Collections.unmodifiableSet(qualifiers);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return delegate.getScope();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return delegate.getStereotypes();
    }

    @Override
    public boolean isAlternative() {
        return delegate.isAlternative();
    }

    @Override
    public Class<?> getBeanClass() {
        return delegate.getBeanClass();
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return delegate.getInjectionPoints();
    }

    @Override
    public boolean isNullable() {
        return delegate.isNullable();
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        return delegate.create(creationalContext);
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        delegate.destroy(instance, creationalContext);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
