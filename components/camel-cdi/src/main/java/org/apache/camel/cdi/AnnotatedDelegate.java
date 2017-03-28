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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import static java.util.stream.Collectors.toSet;

import javax.enterprise.inject.spi.Annotated;

import static org.apache.camel.cdi.CdiSpiHelper.isAnnotationType;

class AnnotatedDelegate implements Annotated {

    private final Annotated delegate;

    private final Set<Annotation> annotations;

    AnnotatedDelegate(Annotated delegate, Set<Annotation> annotations) {
        this.delegate = delegate;
        this.annotations = new HashSet<>(annotations);
    }

    AnnotatedDelegate(Annotated delegate) {
        this.delegate = delegate;
        this.annotations = delegate.getAnnotations();
    }

    public <T extends Annotation> T getAnnotation(Class<T> type) {
        return annotations.stream()
            .filter(isAnnotationType(type))
            .findFirst()
            .map(type::cast)
            .orElse(null);
    }

    public <T extends Annotation> Set<T> getAnnotations(Class<T> type) {
        return annotations.stream()
            .filter(isAnnotationType(type))
            .map(type::cast)
            .collect(toSet());
    }

    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    public Type getBaseType() {
        return delegate.getBaseType();
    }

    public Set<Type> getTypeClosure() {
        return delegate.getTypeClosure();
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> type) {
        return annotations.stream().anyMatch(isAnnotationType(type));
    }

    public String toString() {
        return delegate.toString();
    }
    
    public int hashCode() {
        return delegate.hashCode();
    }
    
    public boolean equals(Object object) {
        return delegate.equals(object);
    }
}
