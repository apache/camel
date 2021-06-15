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
package org.apache.camel.test.junit5.resources;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ExceptionUtils;

import static org.junit.platform.commons.util.ReflectionUtils.isPrivate;
import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

public abstract class BaseResourceManager<T extends Annotation> implements ResourceManager {

    protected final Class<T> annotation;
    protected final ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(getClass());

    public BaseResourceManager(Class<T> annotation) {
        this.annotation = annotation;
    }

    public void inject(ExtensionContext context, Object testInstance, Field field) {
        T ap = field.getAnnotation(annotation);
        verifyType(field, ap);
        try {
            ExtensionContext.Store store = getStore(context, testInstance, field, ap);
            Holder holder = store.getOrComputeIfAbsent(field, f -> createHolder(context, field), Holder.class);
            makeAccessible(field).set(testInstance, holder.get());
        } catch (Throwable t) {
            ExceptionUtils.throwAsUncheckedException(t);
        }
    }

    protected void verifyType(Field field, T ann) {
        if (isPrivate(field)) {
            throw new ExtensionConfigurationException(
                    "@" + ann.annotationType().getSimpleName() + " field [" + field + "] must not be private.");
        }
    }

    protected abstract Holder createHolder(ExtensionContext context, Field field);

    protected Scope.ScopeValue getScope(Field field) {
        return AnnotationUtils.findAnnotation(field, Scope.class)
                .map(Scope::value)
                .orElse(Scope.ScopeValue.Default);
    }

    protected <T extends Annotation> ExtensionContext.Store getStore(
            ExtensionContext context, Object testInstance,
            Field field, T ann) {
        ExtensionContext.Store store;
        Scope.ScopeValue scope = getScope(field);
        switch (scope) {
            case Default:
                store = context.getStore(namespace);
                break;
            case Test:
                if (testInstance == null) {
                    throw new ExtensionConfigurationException(
                            "Scope of @" + ann.annotationType().getSimpleName()
                                                              + " field [" + field
                                                              + "] is set to Test but no test instance is available");
                } else {
                    store = context.getStore(namespace);
                }
                break;
            case Class:
                if (testInstance == null) {
                    store = context.getStore(namespace);
                } else {
                    store = context.getParent().get().getStore(namespace);
                }
                break;
            case Suite:
                if (testInstance == null) {
                    store = context.getParent().get().getStore(namespace);
                } else {
                    store = context.getParent().get().getParent().get().getStore(namespace);
                }
                break;
            default:
                throw new ExtensionConfigurationException(
                        "Scope of @" + ann.annotationType().getSimpleName()
                                                          + " field [" + field + "] is unknown: " + scope);
        }
        return store;
    }

    protected interface Holder extends ExtensionContext.Store.CloseableResource {
        Object get() throws Exception;
    }

}
