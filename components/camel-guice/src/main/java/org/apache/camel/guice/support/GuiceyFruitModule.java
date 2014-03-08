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
package org.apache.camel.guice.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

import org.apache.camel.guice.inject.Configures;
import org.apache.camel.guice.support.internal.MethodKey;

import static com.google.inject.matcher.Matchers.any;
import static org.apache.camel.guice.support.EncounterProvider.encounterProvider;


/**
 * Adds some new helper methods to the base Guice module
 * 
 * @version
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class GuiceyFruitModule extends AbstractModule {

    protected void configure() {
        // lets find all of the configures methods
        List<Method> configureMethods = getConfiguresMethods();
        if (!configureMethods.isEmpty()) {
            final GuiceyFruitModule moduleInstance = this;
            final Class<? extends GuiceyFruitModule> moduleType = getClass();
            TypeLiteral<? extends GuiceyFruitModule> type = TypeLiteral
                    .get(moduleType);

            for (final Method method : configureMethods) {
                int size = method.getParameterTypes().length;
                if (size == 0) {
                    throw new ProvisionException(
                            "No arguments on @Configures method " + method);
                } else if (size > 1) {
                    throw new ProvisionException("Too many arguments " + size
                            + " on @Configures method " + method);
                }
                final Class<?> paramType = getParameterType(type, method, 0);

                bindListener(new AbstractMatcher<TypeLiteral<?>>() {
                    public boolean matches(TypeLiteral<?> typeLiteral) {
                        return typeLiteral.getRawType().equals(paramType);
                    }
                }, new TypeListener() {
                    public <I> void hear(TypeLiteral<I> injectableType,
                            TypeEncounter<I> encounter) {
                        encounter.register(new MembersInjector<I>() {
                            public void injectMembers(I injectee) {
                                // lets invoke the configures method
                                try {
                                    method.setAccessible(true);
                                    method.invoke(moduleInstance, injectee);
                                } catch (IllegalAccessException e) {
                                    throw new ProvisionException(
                                            "Failed to invoke @Configures method "
                                                    + method + ". Reason: " + e,
                                            e);
                                } catch (InvocationTargetException ie) {
                                    Throwable e = ie.getTargetException();
                                    throw new ProvisionException(
                                            "Failed to invoke @Configures method "
                                                    + method + ". Reason: " + e,
                                            e);
                                }
                            }
                        });
                    }
                });
            }
        }
    }

    private List<Method> getConfiguresMethods() {
        List<Method> answer = Lists.newArrayList();
        List<Method> list = Reflectors.getAllMethods(getClass());
        for (Method method : list) {
            if (method.getAnnotation(Configures.class) != null) {
                answer.add(method);
            }
        }
        return answer;
    }

    /**
     * Binds a post injection hook method annotated with the given annotation to
     * the given method handler.
     */
    protected <A extends Annotation> void bindMethodHandler(
            final Class<A> annotationType, final MethodHandler methodHandler) {

        bindMethodHandler(annotationType, encounterProvider(methodHandler));
    }

    /**
     * Binds a post injection hook method annotated with the given annotation to
     * the given method handler.
     */
    protected <A extends Annotation> void bindMethodHandler(
            final Class<A> annotationType,
            final Key<? extends MethodHandler> methodHandlerKey) {

        bindMethodHandler(annotationType, encounterProvider(methodHandlerKey));
    }

    /**
     * Binds a post injection hook method annotated with the given annotation to
     * the given method handler.
     */
    protected <A extends Annotation> void bindMethodHandler(
            final Class<A> annotationType,
            final Class<? extends MethodHandler> methodHandlerType) {

        bindMethodHandler(annotationType, encounterProvider(methodHandlerType));
    }

    private <A extends Annotation> void bindMethodHandler(
            final Class<A> annotationType,
            final EncounterProvider<MethodHandler> encounterProvider) {

        bindListener(any(), new TypeListener() {
            public <I> void hear(TypeLiteral<I> injectableType,
                    TypeEncounter<I> encounter) {
                Class<? super I> type = injectableType.getRawType();
                Method[] methods = type.getDeclaredMethods();
                for (final Method method : methods) {
                    final A annotation = method.getAnnotation(annotationType);
                    if (annotation != null) {
                        final Provider<? extends MethodHandler> provider = encounterProvider
                                .get(encounter);

                        encounter.register(new InjectionListener<I>() {
                            public void afterInjection(I injectee) {

                                MethodHandler methodHandler = provider.get();
                                try {
                                    methodHandler.afterInjection(injectee,
                                            annotation, method);
                                } catch (InvocationTargetException ie) {
                                    Throwable e = ie.getTargetException();
                                    throw new ProvisionException(
                                            e.getMessage(), e);
                                } catch (IllegalAccessException e) {
                                    throw new ProvisionException(
                                            e.getMessage(), e);
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * Binds a custom injection point for a given injection annotation to the
     * annotation member provider so that occurrences of the annotation on
     * fields and methods with a single parameter will be injected by Guice
     * after the constructor and @Inject have been processed.
     * 
     * @param annotationType
     *            the annotation class used to define the injection point
     * @param annotationMemberProviderKey
     *            the key of the annotation member provider which can be
     *            instantiated and injected by guice
     * @param <A>
     *            the annotation type used as the injection point
     */
    protected <A extends Annotation> void bindAnnotationInjector(
            Class<A> annotationType,
            Key<? extends AnnotationMemberProvider> annotationMemberProviderKey) {

        bindAnnotationInjector(annotationType,
                encounterProvider(annotationMemberProviderKey));
    }

    /**
     * Binds a custom injection point for a given injection annotation to the
     * annotation member provider so that occurrences of the annotation on
     * fields and methods with a single parameter will be injected by Guice
     * after the constructor and @Inject have been processed.
     * 
     * @param annotationType
     *            the annotation class used to define the injection point
     * @param annotationMemberProvider
     *            the annotation member provider which can be instantiated and
     *            injected by guice
     * @param <A>
     *            the annotation type used as the injection point
     */
    protected <A extends Annotation> void bindAnnotationInjector(
            Class<A> annotationType,
            AnnotationMemberProvider annotationMemberProvider) {

        bindAnnotationInjector(annotationType,
                encounterProvider(annotationMemberProvider));
    }

    /**
     * Binds a custom injection point for a given injection annotation to the
     * annotation member provider so that occurrences of the annotation on
     * fields and methods with a single parameter will be injected by Guice
     * after the constructor and @Inject have been processed.
     * 
     * @param annotationType
     *            the annotation class used to define the injection point
     * @param annotationMemberProviderType
     *            the type of the annotation member provider which can be
     *            instantiated and injected by guice
     * @param <A>
     *            the annotation type used as the injection point
     */
    protected <A extends Annotation> void bindAnnotationInjector(
            Class<A> annotationType,
            Class<? extends AnnotationMemberProvider> annotationMemberProviderType) {

        bindAnnotationInjector(annotationType,
                encounterProvider(annotationMemberProviderType));
    }

    private <A extends Annotation> void bindAnnotationInjector(
            final Class<A> annotationType,
            final EncounterProvider<AnnotationMemberProvider> memberProviderProvider) {

        bindListener(any(), new TypeListener() {
            Provider<? extends AnnotationMemberProvider> providerProvider;

            public <I> void hear(TypeLiteral<I> injectableType,
                    TypeEncounter<I> encounter) {

                Set<Field> boundFields = Sets.newHashSet();
                Map<MethodKey, Method> boundMethods = Maps.newHashMap();

                TypeLiteral<?> startType = injectableType;
                while (true) {
                    Class<?> type = startType.getRawType();
                    if (type == Object.class) {
                        break;
                    }

                    Field[] fields = type.getDeclaredFields();
                    for (Field field : fields) {
                        if (boundFields.add(field)) {
                            bindAnnotationInjectorToField(encounter, startType,
                                    field);
                        }
                    }

                    Method[] methods = type.getDeclaredMethods();
                    for (final Method method : methods) {
                        MethodKey key = new MethodKey(method);
                        if (boundMethods.get(key) == null) {
                            boundMethods.put(key, method);
                            bindAnnotationInjectionToMember(encounter,
                                    startType, method);
                        }
                    }

                    Class<?> supertype = type.getSuperclass();
                    if (supertype == Object.class) {
                        break;
                    }
                    startType = startType.getSupertype(supertype);
                }
            }

            protected <I> void bindAnnotationInjectionToMember(
                    final TypeEncounter<I> encounter,
                    final TypeLiteral<?> type, final Method method) {
                // TODO lets exclude methods with @Inject?
                final A annotation = method.getAnnotation(annotationType);
                if (annotation != null) {
                    if (providerProvider == null) {
                        providerProvider = memberProviderProvider
                                .get(encounter);
                    }

                    encounter.register(new MembersInjector<I>() {
                        public void injectMembers(I injectee) {
                            AnnotationMemberProvider provider = providerProvider
                                    .get();

                            int size = method.getParameterTypes().length;
                            Object[] values = new Object[size];
                            for (int i = 0; i < size; i++) {
                                Class<?> paramType = getParameterType(type,
                                        method, i);
                                Object value = provider.provide(annotation,
                                        type, method, paramType, i);
                                checkInjectedValueType(value, paramType,
                                        encounter);

                                // if we have a null value then assume the
                                // injection point cannot be satisfied
                                // which is the spring @Autowired way of doing
                                // things
                                if (value == null
                                        && !provider.isNullParameterAllowed(
                                                annotation, method, paramType,
                                                i)) {
                                    return;
                                }
                                values[i] = value;
                            }
                            try {
                                method.setAccessible(true);
                                method.invoke(injectee, values);
                            } catch (IllegalAccessException e) {
                                throw new ProvisionException(
                                        "Failed to inject method " + method
                                                + ". Reason: " + e, e);
                            } catch (InvocationTargetException ie) {
                                Throwable e = ie.getTargetException();
                                throw new ProvisionException(
                                        "Failed to inject method " + method
                                                + ". Reason: " + e, e);
                            }
                        }
                    });
                }
            }

            protected <I> void bindAnnotationInjectorToField(
                    final TypeEncounter<I> encounter,
                    final TypeLiteral<?> type, final Field field) {
                // TODO lets exclude fields with @Inject?
                final A annotation = field.getAnnotation(annotationType);
                if (annotation != null) {
                    if (providerProvider == null) {
                        providerProvider = memberProviderProvider
                                .get(encounter);
                    }

                    encounter.register(new InjectionListener<I>() {
                        public void afterInjection(I injectee) {
                            AnnotationMemberProvider provider = providerProvider
                                    .get();
                            Object value = provider.provide(annotation, type,
                                    field);
                            checkInjectedValueType(value, field.getType(),
                                    encounter);

                            try {
                                field.setAccessible(true);
                                field.set(injectee, value);
                            } catch (IllegalAccessException e) {
                                throw new ProvisionException(
                                        "Failed to inject field " + field
                                                + ". Reason: " + e, e);
                            }
                        }
                    });
                }
            }
        });
    }

    protected Class<?> getParameterType(TypeLiteral<?> type, Method method,
            int i) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        List<TypeLiteral<?>> list = type.getParameterTypes(method);
        TypeLiteral<?> typeLiteral = list.get(i);

        Class<?> paramType = typeLiteral.getRawType();
        if (paramType == Object.class || paramType.isArray()
                && paramType.getComponentType() == Object.class) {
            // if the TypeLiteral ninja doesn't work, lets fall back to the
            // actual type
            paramType = parameterTypes[i];
        }
        return paramType;
    }

    /*
     * protected void bindCloseHook() { bindListener(any(), new Listener() {
     * public <I> void hear(InjectableType<I> injectableType, Encounter<I>
     * encounter) { encounter.registerPostInjectListener(new
     * InjectionListener<I>() { public void afterInjection(I injectee) {
     * 
     * } }); } }); }
     */

    /**
     * Returns true if the value to be injected is of the correct type otherwise
     * an error is raised on the encounter and false is returned
     */
    protected <I> void checkInjectedValueType(Object value, Class<?> type,
            TypeEncounter<I> encounter) {
        // TODO check the type
    }

    /**
     * A helper method to bind the given type with the binding annotation.
     * 
     * This allows you to replace this code
     * <code> bind(Key.get(MyType.class, SomeAnnotation.class))
     * </code>
     * 
     * with this <code> bind(KMyType.class, SomeAnnotation.class) </code>
     */
    protected <T> LinkedBindingBuilder<T> bind(Class<T> type,
            Class<? extends Annotation> annotationType) {
        return bind(Key.get(type, annotationType));
    }

    /**
     * A helper method to bind the given type with the binding annotation.
     * 
     * This allows you to replace this code
     * <code> bind(Key.get(MyType.class, someAnnotation))
     * </code>
     * 
     * with this <code> bind(KMyType.class, someAnnotation) </code>
     */
    protected <T> LinkedBindingBuilder<T> bind(Class<T> type,
            Annotation annotation) {
        return bind(Key.get(type, annotation));
    }

    /**
     * A helper method to bind the given type with the
     * {@link com.google.inject.name.Named} annotation of the given text value.
     * 
     * This allows you to replace this code
     * <code> bind(Key.get(MyType.class, Names.named("myName")))
     * </code>
     * 
     * with this <code> bind(KMyType.class, "myName") </code>
     */
    protected <T> LinkedBindingBuilder<T> bind(Class<T> type, String namedText) {
        return bind(type, Names.named(namedText));
    }

    /**
     * A helper method which binds a named instance to a key defined by the
     * given name and the instances type. So this method is short hand for
     * 
     * <code> bind(instance.getClass(), name).toInstance(instance); </code>
     */
    protected <T> void bindInstance(String name, T instance) {
        // TODO not sure the generics ninja to avoid this cast
        Class<T> aClass = (Class<T>) instance.getClass();
        bind(aClass, name).toInstance(instance);
    }
}