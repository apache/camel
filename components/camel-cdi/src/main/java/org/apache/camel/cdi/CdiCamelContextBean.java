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
import java.util.Collections;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.PassivationCapable;

import org.apache.camel.impl.DefaultCamelContext;

final class CdiCamelContextBean implements Bean<DefaultCamelContext>, PassivationCapable {

    private final Set<Annotation> qualifiers;

    private final Set<Type> types;

    private final InjectionTarget<DefaultCamelContext> target;

    private final String name;

    CdiCamelContextBean(CdiCamelContextAnnotated annotated, InjectionTarget<DefaultCamelContext> target) {
        this.qualifiers = annotated.getAnnotations();
        this.types = annotated.getTypeClosure();
        this.target = target;
        this.name = annotated.isAnnotationPresent(ContextName.class) ? annotated.getAnnotation(ContextName.class).value() : "Default";
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    @Override
    public DefaultCamelContext create(CreationalContext<DefaultCamelContext> creational) {
        DefaultCamelContext context = target.produce(creational);
        target.inject(context, creational);
        target.postConstruct(context);
        creational.push(context);
        return context;
    }

    @Override
    public void destroy(DefaultCamelContext instance, CreationalContext<DefaultCamelContext> creational) {
        target.preDestroy(instance);
        target.dispose(instance);
        creational.release();
    }

    @Override
    public Class<DefaultCamelContext> getBeanClass() {
        return DefaultCamelContext.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public String getName() {
        // Not called as this is not a named bean
        return null;
    }

    @Override
    public String toString() {
        return "Camel context bean [" + name + "]";
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public Set<Type> getTypes() {
        return types;
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public String getId() {
        return getClass().getName() + "[" + name + "]";
    }
}
