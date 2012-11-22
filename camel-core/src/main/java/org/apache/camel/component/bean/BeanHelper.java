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
package org.apache.camel.component.bean;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Helper for the bean component.
 */
public final class BeanHelper {

    private BeanHelper() {
        // utility class
    }

    /**
     * Determines and maps the given value is valid according to the supported
     * values by the bean component.
     *
     * @param value the value
     * @return the parameter type the given value is being mapped as, or <tt>null</tt> if not valid.
     */
    public static Class<?> getValidParameterType(String value) {
        if (ObjectHelper.isEmpty(value)) {
            return null;
        }

        // trim value
        value = value.trim();

        // single quoted is valid
        if (value.startsWith("'") && value.endsWith("'")) {
            return String.class;
        }

        // double quoted is valid
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return String.class;
        }

        // true or false is valid (boolean)
        if (value.equals("true") || value.equals("false")) {
            return Boolean.class;
        }

        // null is valid (to force a null value)
        if (value.equals("null")) {
            return Object.class;
        }

        // simple language tokens is valid
        if (StringHelper.hasStartToken(value, "simple")) {
            return Object.class;
        }

        // numeric is valid
        boolean numeric = true;
        for (char ch : value.toCharArray()) {
            if (!Character.isDigit(ch)) {
                numeric = false;
                break;
            }
        }
        if (numeric) {
            return Number.class;
        }

        // not valid
        return null;
    }

    /**
     * Determines if the given value is valid according to the supported
     * values by the bean component.
     *
     * @param value the value
     * @return <tt>true</tt> if valid, <tt>false</tt> otherwise
     */
    public static boolean isValidParameterValue(String value) {
        if (ObjectHelper.isEmpty(value)) {
            // empty value is valid
            return true;
        }

        return getValidParameterType(value) != null;
    }

    /**
     * Determines if the given parameter type is assignable to the expected type.
     * <p/>
     * This implementation will check if the given parameter type matches the expected type as class using either
     * <ul>
     *     <li>FQN class name - com.foo.MyOrder</li>
     *     <li>Simple class name - MyOrder</li>
     * </ul>
     * If the given parameter type is <b>not</b> a class, then <tt>null</tt> is returned
     *
     * @param resolver          the class resolver
     * @param parameterType     the parameter type as a String, can be a FQN or a simple name of the class
     * @param expectedType      the expected type
     * @return <tt>null</tt> if parameter type is <b>not</b> a class, <tt>true</tt> if parameter type is assignable, <tt>false</tt> if not assignable
     */
    public static Boolean isAssignableToExpectedType(ClassResolver resolver, String parameterType, Class<?> expectedType) {
        // if its a class, then it should be assignable
        Class<?> parameterClass = resolver.resolveClass(parameterType);
        if (parameterClass == null && parameterType.equals(expectedType.getSimpleName())) {
            // it was not the FQN class name, but the simple name instead, which matched
            return true;
        }

        // not a class so return null
        if (parameterClass == null) {
            return null;
        }

        // if there was a class, then it must be assignable to match
        return parameterClass.isAssignableFrom(expectedType);
    }

}
