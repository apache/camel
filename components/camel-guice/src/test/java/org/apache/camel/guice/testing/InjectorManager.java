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
package org.apache.camel.guice.testing;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.guice.support.CloseErrors;
import org.apache.camel.guice.support.CloseFailedException;
import org.apache.camel.guice.support.internal.CloseErrorsImpl;
import org.apache.camel.guice.util.CloseableScope;

/**
 * Used to manage the injectors for the various injection points
 */
public class InjectorManager {
    private static final String NESTED_MODULE_CLASS = "TestModule";

    private Map<Object, Injector> injectors = new ConcurrentHashMap<Object, Injector>();
    private AtomicInteger initializeCounter = new AtomicInteger(0);
    private CloseableScope testScope = new CloseableScope(TestScoped.class);
    private CloseableScope classScope = new CloseableScope(ClassScoped.class);
    
    private boolean closeSingletonsAfterClasses;
    private boolean runFinalizer = true;
    private Class<? extends Module> moduleType;

    public void beforeClasses() {
        int counter = initializeCounter.incrementAndGet();
        if (counter > 1) {
            System.out.println("WARNING! Initialised more than once! Counter: " + counter);
        } else {
            if (runFinalizer) {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            closeSingletons();
                        } catch (Throwable e) {
                            System.out.println("Failed to shut down Guice Singletons: " + e);
                            e.printStackTrace();
                        }
                    }
                });
            }
        }

    }

    /** Lets close all of the injectors we have created so far */
    public void afterClasses() throws CloseFailedException {
        Injector injector = injectors.get(moduleType);
        if (injector != null) {
            classScope.close(injector);
        } else {
            System.out.println("Could not close Class scope as there is no Injector for module type");
        }

        // NOTE that we don't have any good hooks yet to call complete()
        // when the JVM is completed to ensure real singletons shut down
        // correctly
        //
        if (isCloseSingletonsAfterClasses()) {
            closeInjectors();
        }
    }

    public void beforeTest(Object test) throws Exception {
        Preconditions.checkNotNull(test, "test");

        Class<? extends Object> testType = test.getClass();
        moduleType = getModuleForTestClass(testType);

        Injector classInjector = injectors.get(moduleType);
        if (classInjector == null) {
            classInjector = createInjector(moduleType);
            Preconditions.checkNotNull(classInjector, "classInjector");
            injectors.put(moduleType, classInjector);
        }
        injectors.put(testType, classInjector);

        classInjector.injectMembers(test);
    }

    public void afterTest(Object test) throws Exception {
        Injector injector = injectors.get(test.getClass());
        if (injector == null) {
            System.out.println("Warning - no injector available for: " + test);
        } else {
            testScope.close(injector);
        }
    }

    /**
     * Closes down any JVM level singletons used in this testing JVM
     */
    public void closeSingletons() throws CloseFailedException {
        closeInjectors();
    }

    public boolean isCloseSingletonsAfterClasses() {
        return closeSingletonsAfterClasses;
    }

    public void setCloseSingletonsAfterClasses(boolean closeSingletonsAfterClasses) {
        this.closeSingletonsAfterClasses = closeSingletonsAfterClasses;
    }

    protected class TestModule extends AbstractModule {

        protected void configure() {
            bindScope(ClassScoped.class, classScope);
            bindScope(TestScoped.class, testScope);
        }
    }

    protected void closeInjectors() throws CloseFailedException {
        CloseErrors errors = new CloseErrorsImpl(this);
        Set<Entry<Object, Injector>> entries = injectors.entrySet();
        for (Entry<Object, Injector> entry : entries) {
            Injector injector = entry.getValue();
            Injectors.close(injector, errors);
        }
        injectors.clear();
        errors.throwIfNecessary();
    }

    /**
     * Factory method to return the module type that will be used to create an
     * injector.
     * 
     * The default implementation will use the system property
     * <code>org.guiceyfruit.modules</code> (see
     * {@link Injectors#MODULE_CLASS_NAMES} otherwise if that is not set it will
     * look for the {@link UseModule} annotation and use the module defined on
     * that otherwise it will try look for the inner public static class
     * "TestModule"
     * 
     * @see org.apache.camel.guice.testing.UseModule
     * @see #NESTED_MODULE_CLASS
     */
    @SuppressWarnings("unchecked")
    protected Class<? extends Module> getModuleForTestClass(Class<?> objectType)
        throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        String modules = System.getProperty(Injectors.MODULE_CLASS_NAMES);
        if (modules != null) {
            modules = modules.trim();
            if (modules.length() > 0) {
                System.out.println("Overloading Guice Modules: " + modules);
                return null;
            }
        }
        Class<? extends Module> moduleType;
        UseModule config = objectType.getAnnotation(UseModule.class);
        if (config != null) {
            moduleType = config.value();
        } else {
            String name = objectType.getName() + "$" + NESTED_MODULE_CLASS;
            Class<?> type;
            try {
                type = objectType.getClassLoader().loadClass(name);
            } catch (ClassNotFoundException e) {
                try {
                    type = Thread.currentThread().getContextClassLoader().loadClass(name);
                } catch (ClassNotFoundException e2) {
                    throw new ClassNotFoundException(
                                                     "Class "
                                                         + objectType.getName()
                                                         + " does not have a @UseModule annotation nor does it have a nested class called "
                                                         + NESTED_MODULE_CLASS
                                                         + " available on the classpath. Please see: http://code.google.com/p/guiceyfruit/wiki/Testing"
                                                         + e, e);
                }
            }
            try {
                moduleType = (Class<? extends Module>)type;
            } catch (Exception e) {
                throw new IllegalArgumentException("Class " + type.getName() + " is not a Guice Module!", e);
            }
        }
        int modifiers = moduleType.getModifiers();
        if (Modifier.isAbstract(modifiers) || !Modifier.isPublic(modifiers)) {
            throw new IllegalArgumentException("Class " + moduleType.getName()
                                               + " must be a public class which is non abstract");
        }
        try {
            moduleType.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + moduleType.getName()
                                               + " must have a zero argument constructor", e);
        }
        return moduleType;
    }

    /**
     * Creates the injector for the given key
     */
    protected Injector createInjector(Class<? extends Module> moduleType) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException {
        if (moduleType == null) {
            return Injectors.createInjector(System.getProperties(), new TestModule());
        }
        // System.out.println("Creating Guice Injector from module: " +
        // moduleType.getName());
        Module module = moduleType.newInstance();
        return Guice.createInjector(module, new TestModule());
    }
}
