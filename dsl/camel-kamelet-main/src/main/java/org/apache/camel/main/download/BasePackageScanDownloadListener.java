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
package org.apache.camel.main.download;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import javax.inject.Named;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Configuration;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

public class BasePackageScanDownloadListener implements ArtifactDownloadListener, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(BasePackageScanDownloadListener.class);

    private CamelContext camelContext;
    private final Set<String> scanned = new HashSet<>();
    private final boolean packageScanJars;

    public BasePackageScanDownloadListener(boolean packageScanJars) {
        this.packageScanJars = packageScanJars;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onDownloadedFile(File file) {
        String basePackage = camelContext.getCamelContextExtension().getBasePackageScan();
        if (basePackage != null) {
            packageScan(basePackage);
        }
        if (packageScanJars) {
            String ext = FileUtil.onlyExt(file.getName(), true);
            if ("jar".equals(ext)) {
                try {
                    Set<String> packages = new HashSet<>();
                    JarInputStream is = new JarInputStream(new FileInputStream(file));
                    JarEntry entry;
                    while ((entry = is.getNextJarEntry()) != null) {
                        final String name = entry.getName().trim();
                        if (!entry.isDirectory() && name.endsWith(".class")) {
                            packages.add(FileUtil.onlyPath(name));
                        }
                    }
                    if (!packages.isEmpty()) {
                        String[] arr = packages.toArray(new String[0]);
                        packageScan(arr);
                    }
                    IOHelper.close(is);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    public void packageScan(String... basePackage) {
        try {
            basePackageScanConfiguration(basePackage);
            basePackageScanSpring(basePackage);
            basePackageScanQuarkus(basePackage);
        } catch (Exception e) {
            // ignore
        }
    }

    protected void basePackageScanConfiguration(String... basePackage) throws Exception {
        Collection<CamelConfiguration> configs = new ArrayList<>();
        // we only want to scan via isolated classloader
        PackageScanClassResolver pscr = PluginHelper.getPackageScanClassResolver(camelContext);
        Set<Class<?>> found1 = pscr.findImplementations(CamelConfiguration.class, basePackage);
        Set<Class<?>> found2 = pscr.findAnnotated(Configuration.class, basePackage);
        Set<Class<?>> found = new LinkedHashSet<>();
        found.addAll(found1);
        found.addAll(found2);
        for (Class<?> clazz : found) {
            // avoid duplicate if we scan other JARs that can same class from previous downloads
            String fqn = clazz.getName();
            if (scanned.contains(fqn)) {
                continue;
            } else {
                scanned.add(fqn);
            }

            // lets use Camel's injector so the class has some support for dependency injection
            Object config = camelContext.getInjector().newInstance(clazz);
            if (config instanceof CamelConfiguration) {
                LOG.debug("Discovered CamelConfiguration class: {}", clazz);
                CamelConfiguration cc = (CamelConfiguration) config;
                configs.add(cc);
            }
        }

        CamelBeanPostProcessor postProcessor = PluginHelper.getBeanPostProcessor(camelContext);
        // prepare the directly configured instances
        for (Object configuration : configs) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }
        // invoke configure on configurations
        for (CamelConfiguration config : configs) {
            config.configure(camelContext);
        }
    }

    protected void basePackageScanQuarkus(String... basePackage) throws Exception {
        // we only want to scan via isolated classloader
        PackageScanClassResolver pscr = PluginHelper.getPackageScanClassResolver(camelContext);
        Set<Class<?>> found = pscr.findAnnotated(Set.of(ApplicationScoped.class, Singleton.class), basePackage);
        for (Class<?> clazz : found) {
            // avoid duplicate if we scan other JARs that can same class from previous downloads
            String fqn = clazz.getName();
            if (scanned.contains(fqn)) {
                continue;
            } else {
                scanned.add(fqn);
            }

            LOG.debug("Discovered Quarkus @ApplicationScoped/@Singleton class: {}", clazz);

            // @Named can dictate the name of the bean
            String name = null;
            Named named = clazz.getAnnotation(Named.class);
            if (named != null) {
                name = named.value();
            }
            if (name == null || name.isBlank()) {
                name = clazz.getSimpleName();
                // lower case first if using class name
                name = StringHelper.decapitalize(name);
            }
            // must be lazy as we do not know if the bean is in use or not
            Supplier<Object> supplier = () -> camelContext.getInjector().newInstance(clazz, true);
            bindBean(camelContext, clazz, name, supplier, "Quarkus @ApplicationScoped/@Singleton");
        }
    }

    protected void basePackageScanSpring(String... basePackage) throws Exception {
        // we only want to scan via isolated classloader
        PackageScanClassResolver pscr = PluginHelper.getPackageScanClassResolver(camelContext);
        Set<Class<?>> found = pscr.findAnnotated(Set.of(Component.class, Service.class), basePackage);
        for (Class<?> clazz : found) {
            // avoid duplicate if we scan other JARs that can same class from previous downloads
            String fqn = clazz.getName();
            if (scanned.contains(fqn)) {
                continue;
            } else {
                scanned.add(fqn);
            }

            LOG.debug("Discovered Spring @Component/@Service class: {}", clazz);

            String name = null;
            var ann = clazz.getAnnotation(Component.class);
            if (ann != null) {
                name = ann.value();
            } else {
                var ann2 = clazz.getAnnotation(Service.class);
                if (ann2 != null) {
                    name = ann2.value();
                }
            }
            if (name == null || name.isBlank()) {
                name = clazz.getSimpleName();
                // lower case first if using class name
                name = StringHelper.decapitalize(name);
            }
            // must be lazy as we do not know if the bean is in use or not
            Supplier<Object> supplier = () -> camelContext.getInjector().newInstance(clazz, true);
            bindBean(camelContext, clazz, name, supplier, "Spring @Component/@Service");
        }
    }

    private static void bindBean(CamelContext context, Class<?> type, String name, Supplier<Object> supplier, String kind) {
        // to support hot reloading of beans then we need to enable unbind mode in bean post processor
        Registry registry = context.getRegistry();
        CamelBeanPostProcessor bpp = PluginHelper.getBeanPostProcessor(context);
        bpp.setUnbindEnabled(true);
        try {
            // re-bind the bean to the registry
            registry.unbind(name);
            LOG.debug("Lazy binding {} bean: {} of type: {}", kind, name, type);
            registry.bind(name, type, supplier);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        } finally {
            bpp.setUnbindEnabled(false);
        }
    }

}
