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

import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.spi.Injector;

import static org.apache.camel.cdi.BeanManagerHelper.getReferenceByType;

final class CdiCamelInjector implements Injector {

    private final Injector injector;

    private final BeanManager manager;

    CdiCamelInjector(Injector injector, BeanManager manager) {
        this.injector = injector;
        this.manager = manager;
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        return getReferenceByType(manager, type)
            .orElseGet(() -> injector.newInstance(type));
    }

    @Override
    public <T> T newInstance(Class<T> type, Object instance) {
        return injector.newInstance(type, instance);
    }

    @Override
    public boolean supportsAutoWiring() {
        // TODO: cdi to support some kind of @Inject on constructors?
        return false;
    }
}
