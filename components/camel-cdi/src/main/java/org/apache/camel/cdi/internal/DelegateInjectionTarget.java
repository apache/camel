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
package org.apache.camel.cdi.internal;

import java.util.Set;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;

/**
 * A helper class for creating delegate implementations of {@link InjectionTarget}
 */
public abstract class DelegateInjectionTarget implements InjectionTarget {
    private final InjectionTarget delegate;

    public DelegateInjectionTarget(InjectionTarget<Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void dispose(Object instance) {
        delegate.dispose(instance);
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return delegate.getInjectionPoints();
    }

    @Override
    public void inject(Object instance, CreationalContext ctx) {
        delegate.inject(instance, ctx);
    }

    @Override
    public void postConstruct(Object instance) {
        delegate.postConstruct(instance);
    }

    @Override
    public void preDestroy(Object instance) {
        delegate.preDestroy(instance);
    }

    @Override
    public Object produce(CreationalContext creationalContext) {
        return delegate.produce(creationalContext);
    }
}
