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
package org.apache.camel.component.jackson;

import org.apache.camel.Exchange;

public class SchemaHelper {

    public static final String SCHEMA = "schema";
    public static final String VALIDATE = "validate";
    public static final String CONTENT_SCHEMA = "X-Content-Schema";
    public static final String CONTENT_SCHEMA_TYPE = "X-Content-Schema-Type";
    public static final String CONTENT_CLASS = "X-Content-Class";

    private SchemaHelper() {
    }

    /**
     * Helper resolves content class from exchange properties and as a fallback tries to retrieve the content class from
     * the payload body type.
     *
     * @param  exchange the Camel exchange eventually holding content class information in its properties.
     * @param  fallback the fallback content class information when no exchange property is set.
     * @return          the content class as String representation.
     */
    public static String resolveContentClass(Exchange exchange, String fallback) {
        String contentClass = exchange.getProperty(CONTENT_CLASS, String.class);
        if (contentClass != null) {
            return contentClass;
        }

        Object payload = exchange.getMessage().getBody();
        if (payload != null && isPojo(payload.getClass())) {
            return payload.getClass().getName();
        }

        return fallback;
    }

    public static boolean isString(Class<?> type) {
        return String.class.isAssignableFrom(type);
    }

    public static boolean isNumber(Class<?> type) {
        return Number.class.isAssignableFrom(type)
                || int.class.isAssignableFrom(type)
                || long.class.isAssignableFrom(type)
                || short.class.isAssignableFrom(type)
                || char.class.isAssignableFrom(type)
                || float.class.isAssignableFrom(type)
                || double.class.isAssignableFrom(type);
    }

    public static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive()
                || (type.isArray() && type.getComponentType().isPrimitive())
                || char.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type)
                || byte.class.isAssignableFrom(type) || Byte.class.isAssignableFrom(type)
                || boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type);
    }

    public static boolean isPojo(Class<?> type) {
        Package pkg = type.getPackage();
        if (pkg != null) {
            if (pkg.getName().startsWith("java")
                    || pkg.getName().startsWith("javax")
                    || pkg.getName().startsWith("com.sun")
                    || pkg.getName().startsWith("com.oracle")) {
                return false;
            }
        }

        if (isNumber(type)) {
            return false;
        }
        if (isPrimitive(type)) {
            return false;
        }
        if (isString(type)) {
            return false;
        }

        return true;
    }
}
