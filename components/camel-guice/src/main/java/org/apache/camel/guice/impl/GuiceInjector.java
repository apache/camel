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
package org.apache.camel.guice.impl;

import org.apache.camel.IsSingleton;
import org.apache.camel.spi.Injector;

/**
 * An injector which uses Guice to perform the dependency injection of types
 * 
 * @version
 */
public class GuiceInjector implements Injector {
    private final com.google.inject.Injector injector;

    public GuiceInjector(com.google.inject.Injector injector) {
        this.injector = injector;
    }

    public <T> T newInstance(Class<T> type) {
        // TODO if not bound we could create an instance and inject it?
        // injector.injectMembers(instance);
        return injector.getInstance(type);
    }

    public <T> T newInstance(Class<T> type, Object instance) {
        if (instance instanceof IsSingleton) {
            boolean singleton = ((IsSingleton) instance).isSingleton();
            if (singleton) {
                return type.cast(instance);
            }
        }
        return newInstance(type);
    }

    @Override
    public boolean supportsAutoWiring() {
        return false;
    }

}
