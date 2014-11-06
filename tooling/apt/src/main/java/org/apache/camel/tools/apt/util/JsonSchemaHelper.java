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
package org.apache.camel.tools.apt.util;

import static org.apache.camel.tools.apt.util.Strings.doubleQuote;

/**
 * A helper class for <a href="http://json-schema.org/">JSON schema</a>.
 */
public final class JsonSchemaHelper {

    private JsonSchemaHelper() {
    }

    public static String toJson(String name, String type, String description) {
//        if (type.isEnum()) {
//            String typeName = "string";
//            CollectionStringBuffer sb = new CollectionStringBuffer();
//            for (Object value : parameterType.getEnumConstants()) {
//                sb.append(doubleQuote(value.toString()));
//            }
//            return doubleQuote(name) + ": { \"type\": " + doubleQuote(type) + ", \"enum\": [ " + sb.toString() + " ] }";
//        } else if (parameterType.isArray()) {
//            String typeName = "array";
//            return doubleQuote(name) + ": { \"type\": " + doubleQuote(type) + " }";
//        } else {
        String typeName = JsonSchemaHelper.getType(type);
        if ("object".equals(typeName)) {
            // for object then include the javaType as a description so we know that
            return doubleQuote(name) + ": { \"type\": " + doubleQuote(typeName)
                    + ", \"properties\": { \"javaType\": { \"description\": \"" + type + "\", \"type\": \"string\" } } }";
        } else {
            return doubleQuote(name) + ": { \"type\": " + doubleQuote(typeName) + " }";
        }
//        }

    }

    /**
     * Gets the JSon schema type.
     *
     * @param   type the java type
     * @return  the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(String type) {
        // TODO:
//        if (type.isEnum()) {
//            return "enum";
//        } else if (type.isArray()) {
//            return "array";
//        }

        String primitive = getPrimitiveType(type);
        if (primitive != null) {
            return primitive;
        }

        return "object";
    }

    /**
     * Gets the JSon schema primitive type.
     *
     * @param   name the java type
     * @return  the json schema primitive type, or <tt>null</tt> if not a primitive
     */
    public static String getPrimitiveType(String name) {

        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return "string";
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return "array";
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return "array";
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return "array";
            // and these is common as well
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return "string";
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name)) {
            return "boolean";
        } else if ("boolean".equals(name)) {
            return "boolean";
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name)) {
            return "integer";
        } else if ("int".equals(name)) {
            return "integer";
        } else if ("java.lang.Long".equals(name) || "Long".equals(name)) {
            return "integer";
        } else if ("long".equals(name)) {
            return "integer";
        } else if ("java.lang.Short".equals(name) || "Short".equals(name)) {
            return "integer";
        } else if ("short".equals(name)) {
            return "integer";
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name)) {
            return "integer";
        } else if ("byte".equals(name)) {
            return "integer";
        } else if ("java.lang.Float".equals(name) || "Float".equals(name)) {
            return "number";
        } else if ("float".equals(name)) {
            return "number";
        } else if ("java.lang.Double".equals(name) || "Double".equals(name)) {
            return "number";
        } else if ("double".equals(name)) {
            return "number";
        }

        return null;
    }

}
