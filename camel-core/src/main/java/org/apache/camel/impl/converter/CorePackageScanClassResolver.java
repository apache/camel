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
package org.apache.camel.impl.converter;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.component.bean.BeanConverter;
import org.apache.camel.component.file.GenericFileConverter;
import org.apache.camel.converter.AttachmentConverter;
import org.apache.camel.converter.CamelConverter;
import org.apache.camel.converter.CollectionConverter;
import org.apache.camel.converter.DateTimeConverter;
import org.apache.camel.converter.DurationConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.NIOConverter;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.converter.SQLConverter;
import org.apache.camel.converter.TimePatternConverter;
import org.apache.camel.converter.jaxp.DomConverter;
import org.apache.camel.converter.jaxp.StaxConverter;
import org.apache.camel.converter.jaxp.StreamSourceConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.spi.ClassResolver} which loads type converters
 * from a hardcoded list of classes.
 * <p/>
 * <b>Important:</b> Whenever a new type converter class is added to camel-core
 * then the class should be added to the list in this class.
 *
 * @see CoreTypeConverterLoader
 */
public class CorePackageScanClassResolver implements PackageScanClassResolver {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final Set<ClassLoader> classLoaders = new LinkedHashSet<ClassLoader>();
    private final Set<Class<?>> converters = new LinkedHashSet<Class<?>>();

    public CorePackageScanClassResolver() {
        converters.add(ObjectConverter.class);
        converters.add(CollectionConverter.class);
        converters.add(DateTimeConverter.class);
        converters.add(SQLConverter.class);
        converters.add(IOConverter.class);
        converters.add(NIOConverter.class);
        converters.add(StaxConverter.class);
        converters.add(DomConverter.class);
        converters.add(StreamSourceConverter.class);
        converters.add(XmlConverter.class);
        converters.add(CamelConverter.class);
        converters.add(StreamCacheConverter.class);
        converters.add(TimePatternConverter.class);
        converters.add(FutureTypeConverter.class);
        converters.add(BeanConverter.class);
        converters.add(GenericFileConverter.class);
        converters.add(DurationConverter.class);
        converters.add(AttachmentConverter.class);
        converters.add(UriTypeConverter.class);
    }

    @Override
    public void setClassLoaders(Set<ClassLoader> classLoaders) {
        // add all the class loaders
        this.classLoaders.addAll(classLoaders);
    }

    @Override
    public Set<ClassLoader> getClassLoaders() {
        // return a new set to avoid any concurrency issues in other runtimes such as OSGi
        return Collections.unmodifiableSet(new LinkedHashSet<ClassLoader>(classLoaders));
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
}
