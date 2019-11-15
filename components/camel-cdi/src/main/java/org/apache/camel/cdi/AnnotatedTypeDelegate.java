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

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;

final class AnnotatedTypeDelegate<T> extends AnnotatedDelegate implements AnnotatedType<T> {

    private final Set<AnnotatedMethod<? super T>> methods;

    private final AnnotatedType<T> delegate;

    AnnotatedTypeDelegate(AnnotatedType<T> delegate, Set<AnnotatedMethod<? super T>> methods) {
        super(delegate);
        this.delegate = delegate;
        this.methods = new HashSet<>(delegate.getMethods());
        this.methods.removeAll(methods);
        this.methods.addAll(methods);
    }

    @Override
    public Set<AnnotatedConstructor<T>> getConstructors() {
        return delegate.getConstructors();
    }

    @Override
    public Set<AnnotatedField<? super T>> getFields() {
        return delegate.getFields();
    }

    @Override
    public Class<T> getJavaClass() {
        return delegate.getJavaClass();
    }

    @Override
    public Set<AnnotatedMethod<? super T>> getMethods() {
        return methods;
    }
}
