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
import java.util.Set;
import javax.enterprise.inject.spi.Annotated;

// This class is used as a work-around to OWB-1099
final class AnnotatedWrapper implements Annotated {

    private final Annotated delegate;

    AnnotatedWrapper(Annotated delegate) {
        this.delegate = delegate;
    }

    @Override
    public Type getBaseType() {
        return delegate.getBaseType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        return delegate.getTypeClosure();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return delegate.getAnnotation(annotationType);
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return delegate.getAnnotations();
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return delegate.isAnnotationPresent(annotationType);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        AnnotatedWrapper that = (AnnotatedWrapper) object;

        if (!getBaseType().equals(that.getBaseType())) {
            return false;
        } else if (!getTypeClosure().equals(that.getTypeClosure())) {
            return false;
        }
        return getAnnotations().equals(that.getAnnotations());
    }

    @Override
    public int hashCode() {
        int result = getBaseType() != null ? getBaseType().hashCode() : 0;
        result = 31 * result + (getTypeClosure() != null ? getTypeClosure().hashCode() : 0);
        result = 31 * result + (getAnnotations() != null ? getAnnotations().hashCode() : 0);
        return result;
    }
}
