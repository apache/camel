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
package org.apache.camel.impl.converter;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A {@link org.apache.camel.spi.ClassResolver} which loads type converters
 * from an instance that implements {@link org.apache.camel.TypeConverters}.
 * <p/>
 * This is used when adding converters manually using the
 * {@link org.apache.camel.impl.converter.BaseTypeConverterRegistry#addTypeConverters(org.apache.camel.TypeConverters)} method.
 */
public class TypeConvertersPackageScanClassResolver extends ServiceSupport implements PackageScanClassResolver {

    private final Set<ClassLoader> classLoaders = new LinkedHashSet<>();
    private final Set<Class<?>> converters = new LinkedHashSet<>();

    public TypeConvertersPackageScanClassResolver(Class<?> clazz) {
        converters.add(clazz);
        // use the classloader that loaded the class
        classLoaders.add(clazz.getClassLoader());
    }

    @Override
    public Set<ClassLoader> getClassLoaders() {
        // return a new set to avoid any concurrency issues in other runtimes such as OSGi
        return Collections.unmodifiableSet(new LinkedHashSet<>(classLoaders));
    }

    @Override
    public void addClassLoader(ClassLoader classLoader) {
        classLoaders.add(classLoader);
    }

    @Override
    public Set<Class<?>> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
        return converters;
    }

    @Override
    public Set<Class<?>> findAnnotated(Set<Class<? extends Annotation>> annotations, String... packageNames) {
        return converters;
    }

    @Override
    public Set<Class<?>> findImplementations(Class<?> parent, String... packageNames) {
        // noop
        return null;
    }

    @Override
    public Set<Class<?>> findByFilter(PackageScanFilter filter, String... packageNames) {
        // noop
        return null;
    }

    @Override
    public void addFilter(PackageScanFilter filter) {
        // noop
    }

    @Override
    public void removeFilter(PackageScanFilter filter) {
        // noop
    }

    @Override
    public void setAcceptableSchemes(String schemes) {
        // noop
    }

    @Override
    public void clearCache() {
        // noop
    }

}
