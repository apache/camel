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
package org.apache.camel.test.cdi;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.junit.rules.TestRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class CamelCdiRunner extends BlockJUnit4ClassRunner {

    private final CamelCdiContext context = new CamelCdiContext();

    public CamelCdiRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    protected void validateConstructor(List<Throwable> errors) {
        // The test class is instantiated as a CDI bean so we bypass the
        // default JUnit test class constructor constraints validation.
    }

    @Override
    protected void validatePublicVoidNoArgMethods(Class<? extends Annotation> annotation, boolean isStatic, List<Throwable> errors) {
        // Overrides the default validation to allow test methods with
        // parameters so that we can inject CDI references.
        List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(annotation);
        for (FrameworkMethod eachTestMethod : methods) {
            eachTestMethod.validatePublicVoid(isStatic, errors);
        }
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
        List<FrameworkMethod> children = super.getChildren();
        boolean hasDefinedOrder = false;
        for (FrameworkMethod method : children) {
            if (method.getAnnotation(Order.class) != null) {
                hasDefinedOrder = true;
            }
        }
        if (hasDefinedOrder) {
            List<FrameworkMethod> sorted = new ArrayList<>(children);
            Collections.sort(sorted, new FrameworkMethodSorter());
            return sorted;
        }
        return children;
    }

    @Override
    protected List<TestRule> classRules() {
        List<TestRule> rules = super.classRules();
        // Add the CDI container rule before all the other class rules
        // so that it's the last one in FIFO
        rules.add(0, new CamelCdiDeployment(getTestClass(), context));
        return rules;
    }

    @Override
    protected Object createTest() {
        BeanManager manager = context.getBeanManager();
        Set<Bean<?>> beans = manager.getBeans(getTestClass().getJavaClass(), AnyLiteral.INSTANCE);
        Bean<?> bean = beans.iterator().next();
        // TODO: manage lifecycle of @Dependent beans
        return manager.getReference(bean, bean.getBeanClass(), manager.createCreationalContext(bean));
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        return new FrameworkMethodCdiInjection(method, test, context);
    }
}
