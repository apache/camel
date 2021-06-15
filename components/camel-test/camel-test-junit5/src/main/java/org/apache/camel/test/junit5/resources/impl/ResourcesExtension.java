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
package org.apache.camel.test.junit5.resources.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.function.Predicate;

import org.apache.camel.test.junit5.resources.Resource;
import org.apache.camel.test.junit5.resources.ResourceManager;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.ReflectionUtils;

public class ResourcesExtension implements BeforeAllCallback, BeforeEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ResourcesExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        injectFields(context, null, context.getRequiredTestClass(), ReflectionUtils::isStatic);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        context.getRequiredTestInstances().getAllInstances() //
                .forEach(instance -> injectFields(context, instance, instance.getClass(), ReflectionUtils::isNotStatic));
    }

    private void injectFields(ExtensionContext context, Object testInstance, Class<?> testClass, Predicate<Field> predicate) {
        ReflectionUtils.findFields(testClass, predicate, ReflectionUtils.HierarchyTraversalMode.TOP_DOWN).forEach(field -> {
            Resource res = null;
            for (Annotation a : field.getDeclaredAnnotations()) {
                Resource r = a.annotationType().getAnnotation(Resource.class);
                if (r != null) {
                    if (res == null) {
                        res = r;
                    } else {
                        throw new ExtensionConfigurationException(
                                "Field [" + field + "] has more than one @Resource annotation");
                    }
                }
            }
            if (res != null) {
                ResourceManager manager = context.getStore(NAMESPACE).getOrComputeIfAbsent(res.value());
                manager.inject(context, testInstance, field);
            }
        });
    }

}
