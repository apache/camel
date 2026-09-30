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
package org.apache.camel.java.in;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.support.builder.ValueBuilder;

/**
 * Makes a replayed model what the other DSLs have, so tools see one model whatever the DSL.
 * <p/>
 * Classes: the Java DSL keeps a class as a {@link Class} ({@code throwException(Foo.class, "msg")},
 * {@code convertBodyTo(byte[].class)}), where XML and YAML keep its name in a String option next to it
 * ({@code exceptionType}, {@code type}); the name is filled in.
 * <p/>
 * Expressions: they are made plain languages where they can be. The Java DSL keeps an expression clause
 * ({@code setHeader("x").constant("y")}) or a value builder ({@code header("x")}) as an object wrapped in an
 * {@link ExpressionDefinition}, which no DSL can write back; the language inside it ({@code constant}, {@code header},
 * {@code simple}) is what XML and YAML have. A predicate built in Java ({@code header("x").isEqualTo("y")}) has no
 * language and stays as it is.
 */
final class ModelNormalizer {

    private ModelNormalizer() {
    }

    static void normalize(Object root) {
        walk(root, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void walk(Object o, Set<Object> seen) {
        if (o == null || !seen.add(o)) {
            return;
        }
        if (o instanceof Collection<?> c) {
            for (Object e : c) {
                walk(e, seen);
            }
            return;
        }
        if (o instanceof Map<?, ?> m) {
            for (Object e : m.values()) {
                walk(e, seen);
            }
            return;
        }
        if (!o.getClass().getName().startsWith("org.apache.camel.model.")) {
            return;
        }
        if (o instanceof ConstantExpression ce && ce.getExpression() == null && ce.getExpressionValue() != null) {
            // expression().constant(value) keeps the value as a runtime expression; the other DSLs have its text
            ce.setExpression(ce.getExpressionValue().toString());
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            classNames(o, c);
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class
                        || f.getName().equals("parent")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v instanceof ExpressionDefinition ed) {
                        ExpressionDefinition u = unwrapped(ed);
                        if (u != ed && f.getType().isInstance(u)) {
                            f.set(o, u);
                            v = u;
                        }
                    }
                    walk(v, seen);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // a field we cannot reach keeps what it has
                }
            }
        }
    }

    /**
     * Fills in the String option that names a class the Java DSL kept as a Class: for {@code x} or {@code xClass} the
     * first of {@code xName}, {@code xType}, {@code x}, {@code xAsString} that is a String and not set; for a list of
     * classes ({@code exceptionClasses}, {@code exceptionTypes}) the list of names ({@code exceptions}).
     */
    private static void classNames(Object o, Class<?> declaring) {
        for (Field f : declaring.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object v = f.get(o);
                String name = f.getName();
                String base = name.endsWith("Class") ? name.substring(0, name.length() - 5) : name;
                if (v instanceof Class<?> type) {
                    for (String candidate : List.of(name + "Name", base + "Type", base, name + "AsString", base + "Name")) {
                        Field target = field(declaring, candidate);
                        if (target != null && target.getType() == String.class && target != f) {
                            target.setAccessible(true);
                            if (target.get(o) == null) {
                                target.set(o, typeName(type));
                            }
                            break;
                        }
                    }
                } else if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Class<?>
                        && (name.endsWith("Classes") || name.endsWith("Types"))) {
                    String plural = name.substring(0, name.length() - (name.endsWith("Classes") ? 7 : 5)) + "s";
                    Field target = field(declaring, plural);
                    if (target != null && List.class.isAssignableFrom(target.getType())) {
                        target.setAccessible(true);
                        Object names = target.get(o);
                        if (names == null) {
                            names = new ArrayList<String>();
                            target.set(o, names);
                        }
                        if (names instanceof List<?> l && l.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            List<String> strings = (List<String>) l;
                            for (Object t : list) {
                                strings.add(typeName((Class<?>) t));
                            }
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // an option we cannot reach keeps what it has
            }
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** byte[] rather than [B, as the other DSLs write it. */
    private static String typeName(Class<?> type) {
        return type.isArray() ? typeName(type.getComponentType()) + "[]" : type.getName();
    }

    /** The language inside a wrapped clause or value builder; the definition itself when there is none. */
    static ExpressionDefinition unwrapped(ExpressionDefinition ed) {
        if (ed.getClass() != ExpressionDefinition.class) {
            return ed;
        }
        Object v = ed.getExpressionValue() != null ? ed.getExpressionValue() : ed.getPredicate();
        for (int i = 0; i < 5 && v != null; i++) {
            if (v instanceof ExpressionDefinition d) {
                return d;
            } else if (v instanceof ExpressionClause<?> clause) {
                v = clause.getExpressionType() instanceof ExpressionDefinition d ? d : clause.getExpressionValue();
            } else if (v instanceof ValueBuilder vb) {
                v = vb.getExpression();
            } else {
                break;
            }
        }
        return ed;
    }
}
