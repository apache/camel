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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class which will auto-discover converter objects and methods to pre-load
 * the registry of converters on startup
 *
 * @version 
 */
public class AnnotationTypeConverterLoader implements TypeConverterLoader {
    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/TypeConverter";
    private static final transient Logger LOG = LoggerFactory.getLogger(AnnotationTypeConverterLoader.class);
    protected PackageScanClassResolver resolver;
    protected Set<Class<?>> visitedClasses = new HashSet<Class<?>>();
    protected Set<URI> visitedURIs = new HashSet<URI>();

    public AnnotationTypeConverterLoader(PackageScanClassResolver resolver) {
        this.resolver = resolver;
    }

    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
        String[] packageNames;
        try {
            packageNames = findPackageNames();
            if (packageNames == null || packageNames.length == 0) {
                throw new TypeConverterLoaderException("Cannot find package names to be used for classpath scanning for annotated type converters.");
            }
        } catch (Exception e) {
            throw new TypeConverterLoaderException("Cannot find package names to be used for classpath scanning for annotated type converters.", e);
        }

        // if we only have camel-core on the classpath then we have already pre-loaded all its type converters
        // but we exposed the "org.apache.camel.core" package in camel-core. This ensures there is at least one
        // packageName to scan, which triggers the scanning process. That allows us to ensure that we look for
        // type converters in all the JARs.
        if (packageNames.length == 1 && "org.apache.camel.core".equals(packageNames[0])) {
            LOG.debug("No additional package names found in classpath for annotated type converters.");
            // no additional package names found to load type converters so break out
            return;
        }

        Set<Class<?>> classes = resolver.findAnnotated(Converter.class, packageNames);
        if (classes == null || classes.isEmpty()) {
            throw new TypeConverterLoaderException("Cannot find any type converter classes from the following packages: " + Arrays.asList(packageNames));
        }

        LOG.info("Found " + packageNames.length + " packages with " + classes.size() + " @Converter classes to load");

        for (Class type : classes) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Loading converter class: {}", ObjectHelper.name(type));
            }
            loadConverterMethods(registry, type);
        }

        // now clear the maps so we do not hold references
        visitedClasses.clear();
        visitedURIs.clear();
    }

    /**
     * Finds the names of the packages to search for on the classpath looking
     * for text files on the classpath at the {@link #META_INF_SERVICES} location.
     *
     * @return a collection of packages to search for
     * @throws IOException is thrown for IO related errors
     * @throws URISyntaxException 
     */
    protected String[] findPackageNames() throws IOException, URISyntaxException {
        Set<String> packages = new HashSet<String>();
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            findPackages(packages, ccl);
        }
        findPackages(packages, getClass().getClassLoader());
        return packages.toArray(new String[packages.size()]);
    }

    protected void findPackages(Set<String> packages, ClassLoader classLoader) throws IOException, URISyntaxException {
        Enumeration<URL> resources = classLoader.getResources(META_INF_SERVICES);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            URI uri = url.toURI();
            if (!visitedURIs.contains(uri)) {
                // remember we have visited this uri so we wont read it twice
                visitedURIs.add(uri);
                LOG.info("Loading file {} to retrieve list of packages, from url: {}", META_INF_SERVICES, url);
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                try {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        line = line.trim();
                        if (line.startsWith("#") || line.length() == 0) {
                            continue;
                        }
                        tokenize(packages, line);
                    }
                } finally {
                    IOHelper.close(reader, null, LOG);
                }
            }
        }
    }

    /**
     * Tokenizes the line from the META-IN/services file using commas and
     * ignoring whitespace between packages
     */
    private void tokenize(Set<String> packages, String line) {
        StringTokenizer iter = new StringTokenizer(line, ",");
        while (iter.hasMoreTokens()) {
            String name = iter.nextToken().trim();
            if (name.length() > 0) {
                packages.add(name);
            }
        }
    }

    /**
     * Loads all of the converter methods for the given type
     */
    protected void loadConverterMethods(TypeConverterRegistry registry, Class<?> type) {
        if (visitedClasses.contains(type)) {
            return;
        }
        visitedClasses.add(type);
        try {
            Method[] methods = type.getDeclaredMethods();
            CachingInjector<?> injector = null;

            for (Method method : methods) {
                // this may be prone to ClassLoader or packaging problems when the same class is defined
                // in two different jars (as is the case sometimes with specs).
                if (ObjectHelper.hasAnnotation(method, Converter.class, true)) {
                    injector = handleHasConverterAnnotation(registry, type, injector, method);
                } else if (ObjectHelper.hasAnnotation(method, FallbackConverter.class, true)) {
                    injector = handleHasFallbackConverterAnnotation(registry, type, injector, method);
                }
            }

            Class<?> superclass = type.getSuperclass();
            if (superclass != null && !superclass.equals(Object.class)) {
                loadConverterMethods(registry, superclass);
            }
        } catch (NoClassDefFoundError e) {
            LOG.warn("Ignoring converter type: " + type.getCanonicalName() + " as a dependent class could not be found: " + e, e);
        }
    }

    private CachingInjector<?> handleHasConverterAnnotation(TypeConverterRegistry registry, Class<?> type, CachingInjector<?> injector, Method method) {
        if (isValidConverterMethod(method)) {
            int modifiers = method.getModifiers();
            if (isAbstract(modifiers) || !isPublic(modifiers)) {
                LOG.warn("Ignoring bad converter on type: " + type.getCanonicalName() + " method: " + method
                        + " as a converter method is not a public and concrete method");
            } else {
                Class<?> toType = method.getReturnType();
                if (toType.equals(Void.class)) {
                    LOG.warn("Ignoring bad converter on type: " + type.getCanonicalName() + " method: "
                            + method + " as a converter method returns a void method");
                } else {
                    Class<?> fromType = method.getParameterTypes()[0];
                    if (isStatic(modifiers)) {
                        registerTypeConverter(registry, method, toType, fromType,
                                new StaticMethodTypeConverter(method));
                    } else {
                        if (injector == null) {
                            injector = new CachingInjector<Object>(registry, CastUtils.cast(type, Object.class));
                        }
                        registerTypeConverter(registry, method, toType, fromType,
                                new InstanceMethodTypeConverter(injector, method, registry));
                    }
                }
            }
        } else {
            LOG.warn("Ignoring bad converter on type: " + type.getCanonicalName() + " method: " + method
                    + " as a converter method should have one parameter");
        }
        return injector;
    }

    private CachingInjector<?> handleHasFallbackConverterAnnotation(TypeConverterRegistry registry, Class<?> type, CachingInjector<?> injector, Method method) {
        if (isValidFallbackConverterMethod(method)) {
            int modifiers = method.getModifiers();
            if (isAbstract(modifiers) || !isPublic(modifiers)) {
                LOG.warn("Ignoring bad fallback converter on type: " + type.getCanonicalName() + " method: " + method
                        + " as a fallback converter method is not a public and concrete method");
            } else {
                Class<?> toType = method.getReturnType();
                if (toType.equals(Void.class)) {
                    LOG.warn("Ignoring bad fallback converter on type: " + type.getCanonicalName() + " method: "
                            + method + " as a fallback converter method returns a void method");
                } else {
                    if (isStatic(modifiers)) {
                        registerFallbackTypeConverter(registry, new StaticMethodFallbackTypeConverter(method, registry), method);
                    } else {
                        if (injector == null) {
                            injector = new CachingInjector<Object>(registry, CastUtils.cast(type, Object.class));
                        }
                        registerFallbackTypeConverter(registry, new InstanceMethodFallbackTypeConverter(injector, method, registry), method);
                    }
                }
            }
        } else {
            LOG.warn("Ignoring bad fallback converter on type: " + type.getCanonicalName() + " method: " + method
                    + " as a fallback converter method should have one parameter");
        }
        return injector;
    }

    protected void registerTypeConverter(TypeConverterRegistry registry,
                                         Method method, Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        registry.addTypeConverter(toType, fromType, typeConverter);
    }

    protected boolean isValidConverterMethod(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return (parameterTypes != null) && (parameterTypes.length == 1
                || (parameterTypes.length == 2 && Exchange.class.isAssignableFrom(parameterTypes[1])));
    }

    protected void registerFallbackTypeConverter(TypeConverterRegistry registry, TypeConverter typeConverter, Method method) {
        boolean canPromote = false;
        // check whether the annotation may indicate it can promote
        if (method.getAnnotation(FallbackConverter.class) != null) {
            canPromote = method.getAnnotation(FallbackConverter.class).canPromote();
        }
        registry.addFallbackTypeConverter(typeConverter, canPromote);
    }

    protected boolean isValidFallbackConverterMethod(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return (parameterTypes != null) && (parameterTypes.length == 3
                || (parameterTypes.length == 4 && Exchange.class.isAssignableFrom(parameterTypes[1]))
                && (TypeConverterRegistry.class.isAssignableFrom(parameterTypes[parameterTypes.length - 1])));
    }
}
