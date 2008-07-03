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
package org.apache.camel.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


import org.apache.camel.RuntimeCamelException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * A number of useful helper methods for working with Objects
 *
 * @version $Revision$
 */
public final class ObjectHelper {
    private static final transient Log LOG = LogFactory.getLog(ObjectHelper.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ObjectHelper() {
    }

    /**
     * @deprecated use the equal method instead. Will be removed in Camel 2.0.
     *
     * @see #equal(Object, Object)
     */
    @Deprecated
    public static boolean equals(Object a, Object b) {
        return equal(a, b);
    }

    /**
     * A helper method for comparing objects for equality while handling nulls
     */
    public static boolean equal(Object a, Object b) {
        if (a == b) {
            return true;
        }

        if (a instanceof byte[] && b instanceof byte[]) {
            return equalByteArray((byte[]) a, (byte[]) b);
        }

        return a != null && b != null && a.equals(b);
    }

    /**
     * A helper method for comparing byte arrays for equality while handling nulls
     */
    public static boolean equalByteArray(byte[] a, byte[] b) {
        if (a == b) {
            return true;
        }

        // loop and compare each byte
        if (a != null && b != null && a.length == b.length) {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            // all bytes are equal
            return true;
        }

        return false;
    }

    /**
     * Returns true if the given object is equal to any of the expected value
     */
    public static boolean isEqualToAny(Object object, Object... values) {
        for (Object value : values) {
            if (equal(object, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A helper method for performing an ordered comparsion on the objects
     * handling nulls and objects which do not handle sorting gracefully
     */
    public static int compare(Object a, Object b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Comparable) {
            Comparable comparable = (Comparable)a;
            return comparable.compareTo(b);
        } else {
            int answer = a.getClass().getName().compareTo(b.getClass().getName());
            if (answer == 0) {
                answer = a.hashCode() - b.hashCode();
            }
            return answer;
        }
    }

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean)value;
        }
        if (value instanceof String) {
            return "true".equalsIgnoreCase(value.toString()) ? Boolean.TRUE : Boolean.FALSE;
        }
        if (value instanceof Integer) {
            return (Integer)value > 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        return null;
    }

    public static void notNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be specified");
        }
    }

    public static String[] splitOnCharacter(String value, String needle, int count) {
        String rc[] = new String[count];
        rc[0] = value;
        for (int i = 1; i < count; i++) {
            String v = rc[i - 1];
            int p = v.indexOf(needle);
            if (p < 0) {
                return rc;
            }
            rc[i - 1] = v.substring(0, p);
            rc[i] = v.substring(p + 1);
        }
        return rc;
    }

    /**
     * Removes any starting characters on the given text which match the given
     * character
     *
     * @param text the string
     * @param ch the initial characters to remove
     * @return either the original string or the new substring
     */
    public static String removeStartingCharacters(String text, char ch) {
        int idx = 0;
        while (text.charAt(idx) == ch) {
            idx++;
        }
        if (idx > 0) {
            return text.substring(idx);
        }
        return text;
    }

    public static String capitalize(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        String answer = text.substring(0, 1).toUpperCase();
        if (length > 1) {
            answer += text.substring(1, length);
        }
        return answer;
    }


    /**
     * Returns true if the collection contains the specified value
     */
    @SuppressWarnings("unchecked")
    public static boolean contains(Object collectionOrArray, Object value) {
        if (collectionOrArray instanceof Collection) {
            Collection collection = (Collection)collectionOrArray;
            return collection.contains(value);
        } else if (collectionOrArray instanceof String && value instanceof String) {
            String str = (String) collectionOrArray;
            String subStr = (String) value;
            return str.contains(subStr);
        } else {
            Iterator iter = createIterator(collectionOrArray);
            while (iter.hasNext()) {
                if (equal(value, iter.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates an iterator over the value if the value is a collection, an
     * Object[] or a primitive type array; otherwise to simplify the caller's
     * code, we just create a singleton collection iterator over a single value
     */
    @SuppressWarnings("unchecked")
    public static Iterator createIterator(Object value) {
        if (value == null) {
            return Collections.EMPTY_LIST.iterator();
        } else if (value instanceof Collection) {
            Collection collection = (Collection)value;
            return collection.iterator();
        } else if (value.getClass().isArray()) {
            // TODO we should handle primitive array types?
            List<Object> list = Arrays.asList((Object[]) value);
            return list.iterator();
        } else if (value instanceof NodeList) {
            // lets iterate through DOM results after performing XPaths
            final NodeList nodeList = (NodeList) value;
            return new Iterator<Node>() {
                int idx = -1;

                public boolean hasNext() {
                    return ++idx < nodeList.getLength();
                }

                public Node next() {
                    return nodeList.item(idx);
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            return Collections.singletonList(value).iterator();
        }
    }

    /**
     * Returns the predicate matching boolean on a {@link List} result set where
     * if the first element is a boolean its value is used otherwise this method
     * returns true if the collection is not empty
     *
     * @return <tt>true</tt> if the first element is a boolean and its value is true or
     *          if the list is non empty
     */
    public static boolean matches(List list) {
        if (!list.isEmpty()) {
            Object value = list.get(0);
            if (value instanceof Boolean) {
                Boolean flag = (Boolean)value;
                return flag.booleanValue();
            } else {
                // lets assume non-empty results are true
                return true;
            }
        }
        return false;
    }

    public static boolean isNotNullAndNonEmpty(String text) {
        return text != null && text.trim().length() > 0;
    }

    public static boolean isNullOrBlank(String text) {
        return text == null || text.trim().length() <= 0;
    }

    /**
     * A helper method to access a system property, catching any security
     * exceptions
     *
     * @param name the name of the system property required
     * @param defaultValue the default value to use if the property is not
     *                available or a security exception prevents access
     * @return the system property value or the default value if the property is
     *         not available or security does not allow its access
     */
    public static String getSystemProperty(String name, String defaultValue) {
        try {
            return System.getProperty(name, defaultValue);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Caught security exception accessing system property: " + name + ". Reason: " + e,
                          e);
            }
            return defaultValue;
        }
    }

    /**
     * Returns the type name of the given type or null if the type variable is
     * null
     */
    public static String name(Class type) {
        return type != null ? type.getName() : null;
    }

    /**
     * Returns the type name of the given value
     */
    public static String className(Object value) {
        return name(value != null ? value.getClass() : null);
    }

    /**
     * Attempts to load the given class name using the thread context class
     * loader or the class loader used to load this class
     *
     * @param name the name of the class to load
     * @return the class or null if it could not be loaded
     */
    public static Class<?> loadClass(String name) {
        return loadClass(name, ObjectHelper.class.getClassLoader());
    }

    /**
     * Attempts to load the given class name using the thread context class
     * loader or the given class loader
     *
     * @param name the name of the class to load
     * @param loader the class loader to use after the thread context class
     *                loader
     * @return the class or null if it could not be loaded
     */
    public static Class<?> loadClass(String name, ClassLoader loader) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                try {
                    return loader.loadClass(name);
                } catch (ClassNotFoundException e1) {
                    LOG.debug("Could not find class: " + name + ". Reason: " + e);
                }
            }
        }
        return null;
    }

    /**
     * Attempts to load the given resource as a stream using the thread context class
     * loader or the class loader used to load this class
     *
     * @param name the name of the resource to load
     * @return the stream or null if it could not be loaded
     */
    public static InputStream loadResourceAsStream(String name) {
        InputStream in = null;

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            in = contextClassLoader.getResourceAsStream(name);
        }
        if (in == null) {
            in = ObjectHelper.class.getClassLoader().getResourceAsStream(name);
        }

        return in;
    }

    /**
     * A helper method to invoke a method via reflection and wrap any exceptions
     * as {@link RuntimeCamelException} instances
     *
     * @param method the method to invoke
     * @param instance the object instance (or null for static methods)
     * @param parameters the parameters to the method
     * @return the result of the method invocation
     */
    public static Object invokeMethod(Method method, Object instance, Object... parameters) {
        try {
            return method.invoke(instance, parameters);
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeCamelException(e.getCause());
        }
    }

    /**
     * Returns a list of methods which are annotated with the given annotation
     *
     * @param type the type to reflect on
     * @param annotationType the annotation type
     * @return a list of the methods found
     */
    public static List<Method> findMethodsWithAnnotation(Class<?> type,
                                                         Class<? extends Annotation> annotationType) {
        List<Method> answer = new ArrayList<Method>();
        do {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getAnnotation(annotationType) != null) {
                    answer.add(method);
                }
            }
            type = type.getSuperclass();
        } while (type != null);
        return answer;
    }

    /**
     * Turns the given object arrays into a meaningful string
     *
     * @param objects an array of objects or null
     * @return a meaningful string
     */
    public static String asString(Object[] objects) {
        if (objects == null) {
            return "null";
        } else {
            StringBuffer buffer = new StringBuffer("{");
            int counter = 0;
            for (Object object : objects) {
                if (counter++ > 0) {
                    buffer.append(", ");
                }
                String text = (object == null) ? "null" : object.toString();
                buffer.append(text);
            }
            buffer.append("}");
            return buffer.toString();
        }
    }

    /**
     * Returns true if a class is assignable from another class like the
     * {@link Class#isAssignableFrom(Class)} method but which also includes
     * coercion between primitive types to deal with Java 5 primitive type
     * wrapping
     */
    public static boolean isAssignableFrom(Class a, Class b) {
        a = convertPrimitiveTypeToWrapperType(a);
        b = convertPrimitiveTypeToWrapperType(b);
        return a.isAssignableFrom(b);
    }

    /**
     * Converts primitive types such as int to its wrapper type like
     * {@link Integer}
     */
    public static Class convertPrimitiveTypeToWrapperType(Class type) {
        Class rc = type;
        if (type.isPrimitive()) {
            if (type == int.class) {
                rc = Integer.class;
            } else if (type == long.class) {
                rc = Long.class;
            } else if (type == double.class) {
                rc = Double.class;
            } else if (type == float.class) {
                rc = Float.class;
            } else if (type == short.class) {
                rc = Short.class;
            } else if (type == byte.class) {
                rc = Byte.class;
            // TODO: Why is boolean disabled
/*
            } else if (type == boolean.class) {
                rc = Boolean.class;
*/
            }
        }
        return rc;
    }

    /**
     * Helper method to return the default character set name
     */
    public static String getDefaultCharacterSet() {
        return Charset.defaultCharset().name();
    }

    /**
     * Returns the Java Bean property name of the given method, if it is a setter
     */
    public static String getPropertyName(Method method) {
        String propertyName = method.getName();
        if (propertyName.startsWith("set") && method.getParameterTypes().length == 1) {
            propertyName = propertyName.substring(3, 4).toLowerCase() + propertyName.substring(4);
        }
        return propertyName;
    }

    /**
     * Returns true if the given collection of annotations matches the given type
     */
    public static boolean hasAnnotation(Annotation[] annotations, Class<?> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the given resource if it is available, logging any closing exceptions to the given log
     *
     * @param closeable the object to close
     * @param name the name of the resource
     * @param log the log to use when reporting closure warnings
     */
    public static void close(Closeable closeable, String name, Log log) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (log != null) {
                    log.warn("Could not close: " + name + ". Reason: " + e, e);
                }
            }
        }
    }

    /**
     * Converts the given value to the required type or throw a meaningful exception
     */
    public static <T> T cast(Class<T> toType, Object value) {
        if (toType == boolean.class) {
            return (T)cast(Boolean.class, value);
        } else if (toType.isPrimitive()) {
            Class newType = convertPrimitiveTypeToWrapperType(toType);
            if (newType != toType) {
                return (T)cast(newType, value);
            }
        }
        try {
            return toType.cast(value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Failed to convert: " + value + " to type: "
                                               + toType.getName() + " due to: " + e, e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default constructor arguments.
     */
    public static <T> T newInstance(Class<T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeCamelException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * A helper method to create a new instance of a type using the default constructor arguments.
     */
    public static <T> T newInstance(Class<?> actualType, Class<T> expectedType) {
        try {
            Object value = actualType.newInstance();
            return cast(expectedType, value);
        } catch (InstantiationException e) {
            throw new RuntimeCamelException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Returns true if the given name is a valid java identifier
     */
    public static boolean isJavaIdentifier(String name) {
        if (name == null) {
            return false;
        }
        int size = name.length();
        if (size < 1) {
            return false;
        }
        if (Character.isJavaIdentifierStart(name.charAt(0))) {
            for (int i = 1; i < size; i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the type of the given object or null if the value is null
     */
    public static Object type(Object bean) {
        return bean != null ? bean.getClass() : null;
    }

    /**
     * Evaluate the value as a predicate which attempts to convert the value to
     * a boolean otherwise true is returned if the value is not null
     */
    public static boolean evaluateValuePredicate(Object value) {
        if (value instanceof Boolean) {
            Boolean aBoolean = (Boolean)value;
            return aBoolean.booleanValue();
        }
        return value != null;
    }
}
