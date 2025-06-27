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
package org.apache.camel.jandex;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.scan.AnnotatedWithAnyPackageScanFilter;
import org.apache.camel.support.scan.AnnotatedWithPackageScanFilter;
import org.apache.camel.support.scan.AssignableToPackageScanFilter;
import org.apache.camel.support.scan.DefaultPackageScanClassResolver;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("jandex-class-resolver")
public class JandexPackageScanClassResolver extends DefaultPackageScanClassResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JandexPackageScanClassResolver.class);

    private static final String INDEX = "classpath:META-INF/jandex.*";
    private final List<Index> indexes = new ArrayList<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(getCamelContext());
        resolver.addClassLoader(getCamelContext().getApplicationContextClassLoader());
        resolver.start();

        var list = resolver.findResources(INDEX);

        for (Resource res : list) {
            if (res.getLocation().endsWith("jandex.idx")) {
                try (InputStream is = res.getInputStream()) {
                    if (is != null) {
                        LOG.debug("Reading jandex.idx from: {}", res.getLocation());
                        Index index = new IndexReader(is).read();
                        indexes.add(index);
                    }
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        indexes.clear();
    }

    @Override
    protected void find(PackageScanFilter test, String packageName, ClassLoader loader, Set<Class<?>> classes) {
        if (test instanceof AnnotatedWithPackageScanFilter ann) {
            findByAnnotation(ann.getAnnotation(), classes);
        }
        if (test instanceof AnnotatedWithAnyPackageScanFilter ann) {
            for (var c : ann.getAnnotations()) {
                findByAnnotation(c, classes);
            }
        }
        if (test instanceof AssignableToPackageScanFilter ann) {
            for (var c : ann.getParents()) {
                findBySubClass(c, classes);
            }
        }
    }

    private void findByAnnotation(Class<? extends Annotation> c, Set<Class<?>> classes) {
        for (Index index : indexes) {
            for (var ai : index.getAnnotations(c)) {
                var at = ai.target();
                if (at.kind() == AnnotationTarget.Kind.CLASS
                        && at.asClass().nestingType() == ClassInfo.NestingType.TOP_LEVEL) {
                    if (!at.asClass().isAbstract()) {
                        String currentClass = at.asClass().name().toString();
                        for (ClassLoader cl : getClassLoaders()) {
                            try {
                                Class<?> clazz = cl.loadClass(currentClass);
                                classes.add(clazz);
                                break;
                            } catch (ClassNotFoundException e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
    }

    private void findBySubClass(Class<?> c, Set<Class<?>> classes) {
        for (Index index : indexes) {
            if (c.isInterface()) {
                for (var ai : index.getAllKnownImplementations(c)) {
                    if (!ai.asClass().isAbstract()) {
                        String currentClass = ai.asClass().name().toString();
                        for (ClassLoader cl : getClassLoaders()) {
                            try {
                                Class<?> clazz = cl.loadClass(currentClass);
                                classes.add(clazz);
                                break;
                            } catch (ClassNotFoundException e) {
                                // ignore
                            }
                        }
                    }
                }
            } else {
                for (var ai : index.getAllKnownSubclasses(c)) {
                    if (!ai.isAbstract()) {
                        String currentClass = ai.asClass().name().toString();
                        for (ClassLoader cl : getClassLoaders()) {
                            try {
                                Class<?> clazz = cl.loadClass(currentClass);
                                classes.add(clazz);
                                break;
                            } catch (ClassNotFoundException e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
    }

}
