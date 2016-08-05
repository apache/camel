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

import java.util.Collections;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.PassivationCapable;

import static org.apache.camel.cdi.CdiSpiHelper.createBeanId;

class SyntheticBean<T> extends SyntheticBeanAttributes<T> implements Bean<T>, PassivationCapable {

    private final Class<?> type;

    private final InjectionTarget<T> target;

    private final Function<Bean<T>, String> toString;

    SyntheticBean(BeanManager manager, SyntheticAnnotated annotated, Class<?> type, InjectionTarget<T> target, Function<Bean<T>, String> toString) {
        super(manager, annotated);
        this.type = type;
        this.target = target;
        this.toString = toString;
    }

    @Override
    public Class<?> getBeanClass() {
        return type;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        try {
            T instance = target.produce(creationalContext);
            target.inject(instance, creationalContext);
            target.postConstruct(instance);
            return instance;
        } catch (Exception cause) {
            throw new CreationException("Error while instantiating " + this, cause);
        }
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        try {
            target.preDestroy(instance);
            target.dispose(instance);
        } catch (Exception cause) {
            throw new InjectionException("Error while destroying " + this, cause);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public String toString() {
        return toString.apply(this);
    }

    @Override
    public String getId() {
        return new StringJoiner("%")
            .add("CAMEL-CDI")
            .add(getClass().getSimpleName())
            .add(type.getName())
            .add(createBeanId(this))
            .toString();
    }
}
