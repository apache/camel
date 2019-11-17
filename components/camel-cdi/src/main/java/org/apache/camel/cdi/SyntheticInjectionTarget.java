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

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;

@Vetoed
class SyntheticInjectionTarget<T> implements InjectionTarget<T> {

    private final Supplier<T> produce;

    private final Consumer<T> postConstruct;

    private final Consumer<T> preDestroy;

    SyntheticInjectionTarget(Supplier<T> produce) {
        this(produce, t -> {
        });
    }

    SyntheticInjectionTarget(Supplier<T> produce, Consumer<T> postConstruct) {
        this(produce, postConstruct, t -> {
        });
    }

    SyntheticInjectionTarget(Supplier<T> produce, Consumer<T> postConstruct, Consumer<T> preDestroy) {
        this.produce = produce;
        this.postConstruct = postConstruct;
        this.preDestroy = preDestroy;
    }

    @Override
    public void inject(T instance, CreationalContext<T> ctx) {

    }

    @Override
    public void postConstruct(T instance) {
        postConstruct.accept(instance);
    }

    @Override
    public void preDestroy(T instance) {
        preDestroy.accept(instance);
    }

    @Override
    public T produce(CreationalContext<T> ctx) {
        return produce.get();
    }

    @Override
    public void dispose(T instance) {

    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }
}
