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
package org.apache.camel.tooling.util.srcgen;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class GenericType {

    private static final GenericType[] EMPTY = new GenericType[0];

    private static final GenericType ALL = new GenericType(Object.class);

    private static final Map<String, Class> PRIMITIVE_CLASSES = new HashMap<>();

    public enum BoundType {
        Exact,
        Extends,
        Super
    }

    private final Class clazz;
    private final GenericType[] parameters;
    private final BoundType boundType;

    static {
        PRIMITIVE_CLASSES.put("int", int.class);
        PRIMITIVE_CLASSES.put("short", short.class);
        PRIMITIVE_CLASSES.put("long", long.class);
        PRIMITIVE_CLASSES.put("byte", byte.class);
        PRIMITIVE_CLASSES.put("char", char.class);
        PRIMITIVE_CLASSES.put("float", float.class);
        PRIMITIVE_CLASSES.put("double", double.class);
        PRIMITIVE_CLASSES.put("boolean", boolean.class);
        PRIMITIVE_CLASSES.put("void", void.class);
    }

    public GenericType(Type type) {
        this(getConcreteClass(type), parametersOf(type));
    }

    public GenericType(Class clazz, GenericType... parameters) {
        this(clazz, BoundType.Exact, parameters);
    }

    public GenericType(Class clazz, BoundType boundType, GenericType... parameters) {
        this.clazz = clazz;
        this.parameters = parameters;
        this.boundType = boundType;
    }

    public static GenericType parse(String type, ClassLoader loader) throws ClassNotFoundException, IllegalArgumentException {
        type = type.trim();
        // Check if this is an array
        if (type.endsWith("[]")) {
            GenericType t = parse(type.substring(0, type.length() - 2), loader);
            return new GenericType(Array.newInstance(t.getRawClass(), 0).getClass(), t);
        }
        // Check if this is a generic
        int genericIndex = type.indexOf('<');
        if (genericIndex > 0) {
            if (!type.endsWith(">")) {
                throw new IllegalArgumentException("Can not load type: " + type);
            }
            GenericType base = parse(type.substring(0, genericIndex), loader);
            String[] params = type.substring(genericIndex + 1, type.length() - 1).split(",");
            GenericType[] types = new GenericType[params.length];
            for (int i = 0; i < params.length; i++) {
                types[i] = parse(params[i], loader);
            }
            return new GenericType(base.getRawClass(), types);
        }
        // Primitive
        if (isPrimitive(type)) {
            return new GenericType(PRIMITIVE_CLASSES.get(type));
        }
        // Extends
        if (type.startsWith("? extends ")) {
            String raw = type.substring("? extends ".length());
            return new GenericType(loadClass(loader, raw), BoundType.Extends);
        }
        // Super
        if (type.startsWith("? super ")) {
            String raw = type.substring("? extends ".length());
            return new GenericType(loadClass(loader, raw), BoundType.Super);
        }
        // Wildcard
        if (type.equals("?")) {
            return new GenericType(Object.class, BoundType.Extends);
        }
        // Class
        if (loader != null) {
            return new GenericType(loadClass(loader, type));
        } else {
            throw new IllegalArgumentException("Unsupported loader: " + loader);
        }
    }

    static boolean isPrimitive(String type) {
        return PRIMITIVE_CLASSES.containsKey(type);
    }

    private static Class<?> loadClass(ClassLoader loader, String loadClassName) throws ClassNotFoundException {
        Class<?> optionClass;
        String org = loadClassName;
        while (true) {
            try {
                optionClass = loader.loadClass(loadClassName);
                break;
            } catch (ClassNotFoundException e) {
                int dotIndex = loadClassName.lastIndexOf('.');
                if (dotIndex == -1) {
                    throw new ClassNotFoundException(org);
                } else {
                    loadClassName = loadClassName.substring(0, dotIndex) + "$" + loadClassName.substring(dotIndex + 1);
                }
            }
        }
        return optionClass;
    }

    public Class<?> getRawClass() {
        return clazz;
    }

    public GenericType getActualTypeArgument(int i) {
        if (parameters.length == 0) {
            return ALL;
        }
        return parameters[i];
    }

    public int size() {
        return parameters.length;
    }

    @Override
    public String toString() {
        if (parameters.length == 0 && boundType == BoundType.Extends && clazz == Object.class) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        if (boundType == BoundType.Extends) {
            sb.append("? extends ");
        } else if (boundType == BoundType.Super) {
            sb.append("? super ");
        }
        Class cl = getRawClass();
        if (cl.isArray()) {
            if (parameters.length > 0) {
                return parameters[0].toString() + "[]";
            } else {
                return cl.getComponentType().getName() + "[]";
            }
        }
        sb.append(cl.getName());
        if (parameters.length > 0) {
            sb.append("<");
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(parameters[i].toString());
            }
            sb.append(">");
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((boundType == null) ? 0 : boundType.hashCode());
        result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
        result = prime * result + Arrays.hashCode(parameters);
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof GenericType)) {
            return false;
        }
        GenericType other = (GenericType) object;
        if (getRawClass() != other.getRawClass()) {
            return false;
        }
        if (boundType != other.boundType) {
            return false;
        }
        if (parameters == null) {
            return other.parameters == null;
        } else {
            if (other.parameters == null) {
                return false;
            }
            if (parameters.length != other.parameters.length) {
                return false;
            }
            for (int i = 0; i < parameters.length; i++) {
                if (!parameters[i].equals(other.parameters[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    static GenericType bound(GenericType type) {
        if (type.boundType != BoundType.Exact) {
            return new GenericType(type.getRawClass(), BoundType.Exact, type.parameters);
        }
        return type;
    }

    static BoundType boundType(GenericType type) {
        return type.boundType;
    }

    static BoundType boundType(Type type) {
        if (type instanceof WildcardType) {
            WildcardType wct = (WildcardType) type;
            return wct.getLowerBounds().length == 0 ? BoundType.Extends : BoundType.Super;
        }
        return BoundType.Exact;
    }

    static GenericType[] parametersOf(Type type) {
        if (type instanceof Class) {
            Class clazz = (Class) type;
            if (clazz.isArray()) {
                GenericType t = new GenericType(clazz.getComponentType());
                if (t.size() > 0) {
                    return new GenericType[] { t };
                } else {
                    return EMPTY;
                }
            } else {
                return EMPTY;
            }
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] parameters = pt.getActualTypeArguments();
            GenericType[] gts = new GenericType[parameters.length];
            for (int i = 0; i < gts.length; i++) {
                gts[i] = new GenericType(parameters[i]);
            }
            return gts;
        }
        if (type instanceof GenericArrayType) {
            return new GenericType[] { new GenericType(((GenericArrayType) type).getGenericComponentType()) };
        }
        if (type instanceof WildcardType) {
            return EMPTY;
        }
        throw new IllegalStateException();
    }

    static Class<?> getConcreteClass(Type type) {
        Type ntype = collapse(type);
        if (ntype instanceof Class) {
            return (Class<?>) ntype;
        }
        if (ntype instanceof ParameterizedType) {
            return getConcreteClass(collapse(((ParameterizedType) ntype).getRawType()));
        }
        throw new RuntimeException("Unknown type " + type);
    }

    static Type collapse(Type target) {
        if (target instanceof Class || target instanceof ParameterizedType) {
            return target;
        } else if (target instanceof TypeVariable) {
            return collapse(((TypeVariable<?>) target).getBounds()[0]);
        } else if (target instanceof GenericArrayType) {
            Type t = collapse(((GenericArrayType) target).getGenericComponentType());
            while (t instanceof ParameterizedType) {
                t = collapse(((ParameterizedType) t).getRawType());
            }
            return Array.newInstance((Class<?>) t, 0).getClass();
        } else if (target instanceof WildcardType) {
            WildcardType wct = (WildcardType) target;
            if (wct.getLowerBounds().length == 0) {
                return collapse(wct.getUpperBounds()[0]);
            } else {
                return collapse(wct.getLowerBounds()[0]);
            }
        }
        throw new RuntimeException("Huh? " + target);
    }

}
