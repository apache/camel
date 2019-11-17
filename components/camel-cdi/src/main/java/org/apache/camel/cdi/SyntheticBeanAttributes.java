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
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Named;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.apache.camel.cdi.CdiSpiHelper.isAnnotationType;

class SyntheticBeanAttributes<T> {

    private final BeanManager manager;

    private final SyntheticAnnotated annotated;

    SyntheticBeanAttributes(BeanManager manager, SyntheticAnnotated annotated) {
        this.manager = manager;
        this.annotated = annotated;
    }

    <A extends Annotation> void addQualifier(A qualifier) {
        annotated.addAnnotation(qualifier);
    }

    public Class<? extends Annotation> getScope() {
        return annotated.getAnnotations().stream()
            .map(Annotation::annotationType)
            .filter(manager::isScope)
            .findAny()
            .orElse(Dependent.class);
    }

    public Set<Annotation> getQualifiers() {
        return annotated.getAnnotations().stream()
            .filter(a -> manager.isQualifier(a.annotationType()))
            .collect(toSet());
    }

    public String getName() {
        return annotated.getAnnotations().stream()
            .filter(isAnnotationType(Named.class))
            .map(Named.class::cast)
            .map(Named::value)
            .findFirst()
            .orElse(null);
    }

    public Set<Class<? extends Annotation>> getStereotypes() {
        return emptySet();
    }

    public Set<Type> getTypes() {
        return annotated.getTypeClosure();
    }

    public boolean isAlternative() {
        return false;
    }
}
