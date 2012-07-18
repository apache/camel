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
package org.apache.camel.component.cdi.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cdi.CdiCamelContext;
import org.apache.deltaspike.core.api.literal.AnyLiteral;
import org.apache.deltaspike.core.api.literal.DefaultLiteral;

/**
 * Description of camel context bean.
 */
@SuppressWarnings("unchecked")
public class CamelContextBean implements Bean<CdiCamelContext> {

    private final InjectionTarget<CdiCamelContext> target;

    public CamelContextBean(InjectionTarget<CdiCamelContext> injectionTarget) {
        this.target = injectionTarget;
    }

    @Override
    public CdiCamelContext create(CreationalContext<CdiCamelContext> context) {
        CdiCamelContext camelContext = target.produce(context);
        target.postConstruct(camelContext);
        context.push(camelContext);
        return camelContext;
    }

    @Override
    public void destroy(CdiCamelContext instance, CreationalContext<CdiCamelContext> context) {
        target.preDestroy(instance);
        target.dispose(instance);
        context.release();
    }

    @Override
    public Set<Type> getTypes() {
        return new HashSet<Type>(Arrays.asList(Object.class, CamelContext.class, CdiCamelContext.class));
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return new HashSet<Annotation>(Arrays.asList(new DefaultLiteral(), new AnyLiteral()));
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public String getName() {
        return "CamelContext";
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return target.getInjectionPoints();
    }

    @Override
    public Class<?> getBeanClass() {
        return CdiCamelContext.class;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return null;
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

}
