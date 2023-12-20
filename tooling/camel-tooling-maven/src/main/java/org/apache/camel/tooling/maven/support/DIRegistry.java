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
package org.apache.camel.tooling.maven.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.support.SupplierRegistry;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;

/**
 * <p>
 * {@link SupplierRegistry} extension that allows registration of bean <em>recipes</em> based on jakarta.inject
 * annotations.
 * </p>
 *
 * <p>
 * Such requirement was found when trying to configure maven-resolver without using the deprecated service locator
 * helpers (see <a href="https://issues.apache.org/jira/browse/MRESOLVER-157">MRESOLVER-157</a>).
 * </p>
 */
public class DIRegistry extends SupplierRegistry {

    private final Map<Class<?>, List<Object>> byClass = new HashMap<>();
    private final Set<Supplier<?>> underConstruction = Collections.synchronizedSet(new HashSet<>());

    /**
     * Registration of a bean with the same lookup and target class.
     *
     * @param type
     */
    public void bind(Class<?> type) {
        bind(type, type);
    }

    private static boolean hasInjectAnnotation(Constructor<?> ctr) {
        if (ctr.getAnnotation(Inject.class) != null) {
            return true;
        }

        for (Annotation a : ctr.getAnnotations()) {
            String s = a.annotationType().getName();
            if ("javax.inject.Inject".equals(s)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNamedAnnotation(Annotation ann) {
        if (Named.class == ann.annotationType()) {
            return true;
        }

        // backwards comp
        String s = ann.annotationType().getName();
        return "javax.inject.Named".equals(s);
    }

    private static String getNamedAnnotationValue(Class<?> type) {
        Named ann = type.getAnnotation(Named.class);
        if (ann != null) {
            return ann.value();
        }

        for (Annotation a : type.getAnnotations()) {
            if (isNamedAnnotation(a)) {
                String s = a.toString();
                // @javax.inject.Named("valueHere")
                return StringHelper.between(s, "(\"", "\")");
            }
        }

        return null;
    }

    /**
     * Main "registration" method, where {@code beanClass} is expected to be a pojo class with non-default constructor
     * annotated with {@link Inject}. The class may be annotated with {@link Named}. (Maybe supporting
     * {@link jakarta.inject.Singleton} soon).
     *
     * @param key  the lookup type
     * @param type the actual type (to use when instantiating a bean)
     */
    public void bind(Class<?> key, Class<?> type) {
        String name = key.getName();
        for (Annotation ann : type.getAnnotations()) {
            if (isNamedAnnotation(ann)) {
                name = getNamedAnnotationValue(type);
                if (name == null || name.isBlank()) {
                    name = key.getName();
                }
            }
        }

        Constructor<?> defaultConstructor = null;
        Comparator<Constructor<?>> byParamCount = Comparator.<Constructor<?>> comparingInt(Constructor::getParameterCount)
                .reversed();
        Set<Constructor<?>> constructors = new TreeSet<>(byParamCount);
        for (Constructor<?> ctr : type.getDeclaredConstructors()) {
            if (ctr.getParameterCount() == 0) {
                defaultConstructor = ctr;
            } else {
                if (hasInjectAnnotation(ctr)) {
                    constructors.add(ctr);
                }
            }
        }

        if (constructors.isEmpty() && defaultConstructor != null) {
            // no need to lazy evaluate such bean
            try {
                Object instance = defaultConstructor.newInstance();
                bind(name, key, instance);
                return;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException("Problem registering bean of " + type.getName() + " type");
            }
        }

        if (!constructors.isEmpty()) {
            Constructor<?> ctr = constructors.iterator().next();
            // dependency-cycle alert!
            final Type[] parameterTypes = ctr.getGenericParameterTypes();
            Supplier<?> lazyCreator = new Supplier<>() {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                @Override
                public Object get() {
                    if (underConstruction.contains(this)) {
                        throw new IllegalStateException(
                                "Cyclic dependency found when creating bean of "
                                                        + type.getName() + " type");
                    }
                    underConstruction.add(this);
                    try {
                        final Object[] parameters = new Object[parameterTypes.length];
                        int pc = 0;
                        for (Type pt : parameterTypes) {
                            Class<?> t = null;
                            Object param = null;
                            if (pt instanceof ParameterizedType) {
                                Class<?> rawType = (Class<?>) ((ParameterizedType) pt).getRawType();
                                // when it's not a collection/map, skip the type parameter part (for now)
                                Type[] typeArguments = ((ParameterizedType) pt).getActualTypeArguments();
                                if (Collection.class.isAssignableFrom(rawType)) {
                                    if (typeArguments.length == 1) {
                                        // set or list (for now)
                                        Type vType = typeArguments[0];
                                        t = rawType;
                                        if (Set.class == rawType) {
                                            param = new LinkedHashSet<>();
                                            Map<String, ?> values = findByTypeWithName((Class<?>) vType);
                                            ((Set) param).addAll(values.values());
                                        } else if (List.class == rawType) {
                                            param = new ArrayList<>();
                                            Map<String, ?> values = findByTypeWithName((Class<?>) vType);
                                            ((List) param).addAll(values.values());
                                        }
                                    }
                                } else if (Map.class == rawType) {
                                    if (typeArguments.length == 2) {
                                        // first type must be String (name - from @Named or FQCN)
                                        Type vType = typeArguments[1];
                                        t = rawType;
                                        param = new LinkedHashMap<>();
                                        Map<String, ?> values = findByTypeWithName((Class<?>) vType);
                                        ((Map) param).putAll(values);
                                    }
                                } else {
                                    t = rawType;
                                }
                            } else if (pt instanceof Class) {
                                t = (Class<?>) pt;
                                if (t.isArray()) {
                                    Map<String, ?> values = findByTypeWithName(t.getComponentType());
                                    param = Array.newInstance(t.getComponentType(), values.size());
                                    System.arraycopy(values.values().toArray(), 0, param, 0, values.size());
                                }
                            }
                            if (t == null) {
                                throw new IllegalArgumentException(
                                        "Can't handle argument of " + pt
                                                                   + " type when creating bean of " + type.getName() + " type");
                            }
                            if (param == null) {
                                List<Object> instances = byClass.get(t);
                                if (instances == null) {
                                    throw new IllegalArgumentException(
                                            "Missing " + t.getName()
                                                                       + " instance when creating bean of " + type.getName()
                                                                       + " type");
                                }
                                if (instances.size() > 1) {
                                    throw new IllegalArgumentException(
                                            "Ambiguous parameter of " + t.getName()
                                                                       + " when creating bean of " + type.getName() + " type");
                                }
                                param = instances.get(0);
                            }
                            // this is where recursion may happen.
                            parameters[pc++] = param instanceof Supplier ? ((Supplier<?>) param).get() : param;
                        }
                        try {
                            ctr.setAccessible(true);
                            return ctr.newInstance(parameters);
                        } catch (InstantiationException | IllegalAccessException
                                 | InvocationTargetException | IllegalArgumentException e) {
                            throw new IllegalArgumentException(
                                    "Problem instantiating bean of "
                                                               + type.getName() + " type",
                                    e);
                        }
                    } finally {
                        underConstruction.remove(this);
                    }
                }
            };
            bind(name, key, Suppliers.memorize(lazyCreator));
        }
    }

    /**
     * Make an {@code alias} point to the same target bean as existing {@code key}.
     *
     * @param alias
     * @param key
     */
    public void alias(Class<?> alias, Class<?> key) {
        if (byClass.containsKey(key)) {
            List<Object> recipes = byClass.get(key);
            byClass.put(alias, recipes);
            String id = alias.getName();
            if (recipes.size() > 1) {
                throw new IllegalArgumentException("Multiple recipes for " + key.getName() + " type");
            }
            computeIfAbsent(id, k -> new LinkedHashMap<>()).put(alias, recipes.get(0));
        }
    }

    @Override
    public void bind(String id, Class<?> type, Object bean) {
        byClass.computeIfAbsent(type, c -> new ArrayList<>()).add(bean);
        super.bind(id, type, bean);
    }

    @Override
    public void bind(String id, Class<?> type, Supplier<Object> bean) {
        byClass.computeIfAbsent(type, c -> new ArrayList<>()).add(bean);
        super.bind(id, type, bean);
    }

    @Override
    public void bindAsPrototype(String id, Class<?> type, Supplier<Object> bean) {
        byClass.computeIfAbsent(type, c -> new ArrayList<>()).add(bean);
        super.bindAsPrototype(id, type, bean);
    }

    @SuppressWarnings("unchecked")
    public <T> T lookupByClass(Class<T> cls) {
        List<Object> instances = byClass.get(cls);
        if (instances == null) {
            return null;
        }
        if (instances.size() > 1) {
            throw new IllegalArgumentException("Multiple beans of " + cls.getName() + " type available");
        }
        Object instance = instances.get(0);
        return (T) (instance instanceof Supplier ? ((Supplier<?>) instance).get() : instance);
    }

}
