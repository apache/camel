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

import org.apache.camel.IsSingleton;
import org.apache.camel.spi.Injector;
import org.apache.camel.util.ReflectionInjector;
import org.apache.deltaspike.core.api.provider.BeanProvider;

/**
 * Injector implementation which performs injection with CDI bean provider.
 */
public class CdiInjector implements Injector {

    /**
     * Fallback injector used when there is bean of given type registered in CDI.
     */
    private Injector injector;

    public CdiInjector() {
        this(new ReflectionInjector());
    }

    public CdiInjector(Injector parent) {
        this.injector = parent;
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        T bean = BeanProvider.getContextualReference(type, true);
        if (bean != null) {
            return type.cast(bean);
        }
        return injector.newInstance(type);
    }

    @Override
    public <T> T newInstance(Class<T> type, Object instance) {
        if (instance instanceof IsSingleton) {
            boolean singleton = ((IsSingleton) instance).isSingleton();
            if (singleton) {
                return type.cast(instance);
            }
        }
        return newInstance(type);
    }

}
