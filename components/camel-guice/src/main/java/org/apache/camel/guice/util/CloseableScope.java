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
package org.apache.camel.guice.util;

import java.lang.annotation.Annotation;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;

import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.guice.support.CloseFailedException;
import org.apache.camel.guice.support.HasScopeAnnotation;
import org.apache.camel.guice.support.internal.CloseErrorsImpl;


/**
 * Represents a scope which caches objects around until the scope is closed.
 * 
 * The scope can be closed as many times as required - there is no need to
 * recreate the scope instance each time a scope goes out of scope.
 * 
 * @version
 */
public class CloseableScope implements Scope, HasScopeAnnotation {

    private Class<? extends Annotation> scopeAnnotation;
    private final Map<Key<?>, Object> map = Maps.newHashMap();

    @Inject
    private Injector injector;

    public CloseableScope(Class<? extends Annotation> scopeAnnotation) {
        this.scopeAnnotation = scopeAnnotation;
    }

    @SuppressWarnings("unchecked")
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
        return new CachingProvider<T>() {
            public T get() {
                Object o;
                synchronized (map) {
                    o = map.get(key);
                    if (o == null) {
                        o = creator.get();
                        map.put(key, o);
                    }
                }
                return (T) o;
            }

            public T getCachedValue() {
                synchronized (map) {
                    return (T) map.get(key);
                }
            }
        };
    }

    /**
     * Closes all of the objects within this scope using the given injector and
     * scope annotation and clears the scope
     */
    public void close() throws CloseFailedException {
        close(injector);
    }

    /**
     * Closes all of the objects within the given injector of the specified
     * scope and clears the scope
     */
    public void close(Injector injector) throws CloseFailedException {
        Preconditions.checkNotNull(injector, "injector");
        CloseErrorsImpl errors = new CloseErrorsImpl(this);
        Injectors.close(injector, scopeAnnotation, errors);

        synchronized (map) {
            map.clear();
        }
        errors.throwIfNecessary();
    }

    public Class<? extends Annotation> getScopeAnnotation() {
        return scopeAnnotation;
    }
}
