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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

/**
 * A class which will auto-discover {@link Converter} objects and methods to pre-load
 * the {@link TypeConverterRegistry} of converters on startup.
 * <p/>
 * This implementation supports scanning for type converters in JAR files. The {@link #META_INF_SERVICES}
 * contains a list of packages or FQN class names for {@link Converter} classes. The FQN class names
 * is loaded first and directly by the class loader.
 * <p/>
 * The {@link PackageScanClassResolver} is being used to scan packages for {@link Converter} classes and
 * this procedure is slower than loading the {@link Converter} classes directly by its FQN class name.
 * Therefore its recommended to specify FQN class names in the {@link #META_INF_SERVICES} file.
 * Likewise the procedure for scanning using {@link PackageScanClassResolver} may require custom implementations
 * to work in various containers such as JBoss, OSGi, etc.
 */
public class AnnotationTypeConverterLoader implements TypeConverterLoader {
    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/TypeConverter";
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationTypeConverterLoader.class);
    private static final Charset UTF8 = Charset.forName("UTF-8");
    protected PackageScanClassResolver resolver;
    protected Set<Class<?>> visitedClasses = new HashSet<>();
    protected Set<String> visitedURIs = new HashSet<>();

    public AnnotationTypeConverterLoader(PackageScanClassResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
        String[] packageNames;

        LOG.trace("Searching for {} services", META_INF_SERVICES);
        try {
            packageNames = findPackageNames();
            if (packageNames == null || packageNames.length == 0) {
                LOG.debug("No package names found to be used for classpath scanning for annotated type converters.");
                return;
            }
        } catch (Exception e) {
            throw new TypeConverterLoaderException("Cannot find package names to be used for classpath scanning for annotated type converters.", e);
        }

        // if we only have camel-core on the classpath then we have already pre-loaded all its type converters
        // but we exposed the "org.apache.camel.core" package in camel-core. This ensures there is at least one
        // packageName to scan, which triggers the scanning process. That allows us to ensure that we look for
        // META-INF/services in all the JARs.
        if (packageNames.length == 1 && "org.apache.camel.core".equals(packageNames[0])) {
            LOG.debug("No additional package names found in classpath for annotated type converters.");
            // no additional package names found to load type converters so break out
            return;
        }

        // now filter out org.apache.camel.core as its not needed anymore (it was just a dummy)
        packageNames = filterUnwantedPackage("org.apache.camel.core", packageNames);

        // filter out package names which can be loaded as a class directly so we avoid package scanning which
        // is much slower and does not work 100% in all runtime containers
        Set<Class<?>> classes = new HashSet<>();
        packageNames = filterPackageNamesOnly(resolver, packageNames, classes);
        if (!classes.isEmpty()) {
            LOG.debug("Loaded {} @Converter classes", classes.size());
        }

        // if there is any packages to scan and load @Converter classes, then do it
        if (packageNames != null && packageNames.length > 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found converter packages to scan: {}", String.join(", ", packageNames));
            }
            Set<Class<?>> scannedClasses = resolver.findAnnotated(Converter.class, packageNames);
            if (scannedClasses.isEmpty()) {
                throw new TypeConverterLoaderException("Cannot find any type converter classes from the following packages: " + Arrays.asList(packageNames));
            }
            LOG.debug("Found {} packages with {} @Converter classes to load", packageNames.length, scannedClasses.size());
            classes.addAll(scannedClasses);
        }

        // load all the found classes into the type converter registry
        for (Class<?> type : classes) {
            if (acceptClass(type)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Loading converter class: {}", ObjectHelper.name(type));
                }
                loadConverterMethods(registry, type);
            }
        }

        // now clear the maps so we do not hold references
        visitedClasses.clear();
        visitedURIs.clear();
    }

    /**
     * Filters the given list of packages and returns an array of <b>only</b> package names.
     * <p/>
     * This implementation will check the given list of packages, and if it contains a class name,
     * that class will be loaded directly and added to the list of classes. This optimizes the
     * type converter to avoid excessive file scanning for .class files.
     *
     * @param resolver the class resolver
     * @param packageNames the package names
     * @param classes to add loaded @Converter classes
     * @return the filtered package names
     */
    protected String[] filterPackageNamesOnly(PackageScanClassResolver resolver, String[] packageNames, Set<Class<?>> classes) {
        if (packageNames == null || packageNames.length == 0) {
            return packageNames;
        }

        // optimize for CorePackageScanClassResolver
        if (resolver.getClassLoaders().isEmpty()) {
            return packageNames;
        }

        // the filtered packages to return
        List<String> packages = new ArrayList<>();

        // try to load it as a class first
        for (String name : packageNames) {
            // must be a FQN class name by having an upper case letter
            if (StringHelper.isClassName(name)) {
                Class<?> clazz = null;
                for (ClassLoader loader : resolver.getClassLoaders()) {
                    try {
                        clazz = ObjectHelper.loadClass(name, loader);
                        if (clazz != null) {
                            LOG.trace("Loaded {} as class {}", name, clazz);
                            classes.add(clazz);
                            // class found, so no need to load it with another class loader
                        }
                        break;
                    } catch (Throwable e) {
                        // do nothing here
                    }
                }
                if (clazz == null) {
                    // ignore as its not a class (will be package scan afterwards)
                    packages.add(name);
                }
            } else {
                // ignore as its not a class (will be package scan afterwards)
                packages.add(name);
            }
        }

        // return the packages which is not FQN classes
        return packages.toArray(new String[packages.size()]);
    }

    /**
     * Finds the names of the packages to search for on the classpath looking
     * for text files on the classpath at the {@link #META_INF_SERVICES} location.
     *
     * @return a collection of packages to search for
     * @throws IOException is thrown for IO related errors
     */
    protected String[] findPackageNames() throws IOException {
        Set<String> packages = new HashSet<>();
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            findPackages(packages, ccl);
        }
        findPackages(packages, getClass().getClassLoader());
        return packages.toArray(new String[packages.size()]);
    }

    protected void findPackages(Set<String> packages, ClassLoader classLoader) throws IOException {
        Enumeration<URL> resources = classLoader.getResources(META_INF_SERVICES);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String path = url.getPath();
            if (!visitedURIs.contains(path)) {
                // remember we have visited this uri so we wont read it twice
                visitedURIs.add(path);
                LOG.debug("Loading file {} to retrieve list of packages, from url: {}", META_INF_SERVICES, url);
                BufferedReader reader = IOHelper.buffered(new InputStreamReader(url.openStream(), UTF8));
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
                    boolean allowNull = false;
                    if (method.getAnnotation(Converter.class) != null) {
                        allowNull = method.getAnnotation(Converter.class).allowNull();
                    }
                    boolean fallback = method.getAnnotation(Converter.class).fallback();
                    if (fallback) {
                        injector = handleHasFallbackConverterAnnotation(registry, type, injector, method, allowNull);
                    } else {
                        injector = handleHasConverterAnnotation(registry, type, injector, method, allowNull);
                    }
                }
            }

            Class<?> superclass = type.getSuperclass();
            if (superclass != null && !superclass.equals(Object.class)) {
                loadConverterMethods(registry, superclass);
            }
        } catch (NoClassDefFoundError e) {
            boolean ignore = false;
            // does the class allow to ignore the type converter when having load errors
            if (ObjectHelper.hasAnnotation(type, Converter.class, true)) {
                if (type.getAnnotation(Converter.class) != null) {
                    ignore = type.getAnnotation(Converter.class).ignoreOnLoadError();
                }
            }
            // if we should ignore then only log at debug level
            if (ignore) {
                LOG.debug("Ignoring converter type: " + type.getCanonicalName() + " as a dependent class could not be found: " + e, e);
            } else {
                LOG.warn("Ignoring converter type: " + type.getCanonicalName() + " as a dependent class could not be found: " + e, e);
            }
        }
    }

    protected boolean acceptClass(Class<?> clazz) {
        return true;
    }

    private CachingInjector<?> handleHasConverterAnnotation(TypeConverterRegistry registry, Class<?> type,
                                                            CachingInjector<?> injector, Method method, boolean allowNull) {
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
                                new StaticMethodTypeConverter(method, allowNull));
                    } else {
                        if (injector == null) {
                            injector = new CachingInjector<>(registry, CastUtils.cast(type, Object.class));
                        }
                        registerTypeConverter(registry, method, toType, fromType,
                                new InstanceMethodTypeConverter(injector, method, registry, allowNull));
                    }
                }
            }
        } else {
            LOG.warn("Ignoring bad converter on type: " + type.getCanonicalName() + " method: " + method
                    + " as a converter method should have one parameter");
        }
        return injector;
    }

    private CachingInjector<?> handleHasFallbackConverterAnnotation(TypeConverterRegistry registry, Class<?> type,
                                                                    CachingInjector<?> injector, Method method, boolean allowNull) {
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
                        registerFallbackTypeConverter(registry, new StaticMethodFallbackTypeConverter(method, registry, allowNull), method);
                    } else {
                        if (injector == null) {
                            injector = new CachingInjector<>(registry, CastUtils.cast(type, Object.class));
                        }
                        registerFallbackTypeConverter(registry, new InstanceMethodFallbackTypeConverter(injector, method, registry, allowNull), method);
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
        if (method.getAnnotation(Converter.class) != null) {
            canPromote = method.getAnnotation(Converter.class).fallbackCanPromote();
        }
        registry.addFallbackTypeConverter(typeConverter, canPromote);
    }

    protected boolean isValidFallbackConverterMethod(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return (parameterTypes != null) && (parameterTypes.length == 3
                || (parameterTypes.length == 4 && Exchange.class.isAssignableFrom(parameterTypes[1]))
                && (TypeConverterRegistry.class.isAssignableFrom(parameterTypes[parameterTypes.length - 1])));
    }

    /**
     * Filters the given list of packages
     *
     * @param name  the name to filter out
     * @param packageNames the packages
     * @return he packages without the given name
     */
    protected static String[] filterUnwantedPackage(String name, String[] packageNames) {
        // the filtered packages to return
        List<String> packages = new ArrayList<>();

        for (String s : packageNames) {
            if (!name.equals(s)) {
                packages.add(s);
            }
        }

        return packages.toArray(new String[packages.size()]);
    }

}
