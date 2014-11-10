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
package org.apache.camel.tools.apt;

import java.util.Set;

/**
 * A helper class for <a href="http://json-schema.org/">JSON schema</a>.
 */
final class JsonSchemaHelper {

    private static final String VALID_CHARS = ".-='/\\!&()";

    private JsonSchemaHelper() {
    }

    public static String toJson(String name, String type, String defaultValue, String description, boolean enumType, Set<String> enums) {
        String typeName = JsonSchemaHelper.getType(type, enumType);

        StringBuilder sb = new StringBuilder();
        sb.append(Strings.doubleQuote(name));
        sb.append(": { \"type\": ");

        if ("enum".equals(typeName)) {
            sb.append(Strings.doubleQuote("string"));
            sb.append(", \"javaType\": \"" + type + "\"");
            CollectionStringBuffer enumValues = new CollectionStringBuffer();
            for (Object value : enums) {
                enumValues.append(Strings.doubleQuote(value.toString()));
            }
            sb.append(", \"enum\": [ ");
            sb.append(enumValues.toString());
            sb.append(" ]");
        } else if ("array".equals(typeName)) {
            sb.append(Strings.doubleQuote("array"));
            sb.append(", \"javaType\": \"" + type + "\"");
        } else {
            sb.append(Strings.doubleQuote(typeName));
            sb.append(", \"javaType\": \"" + type + "\"");
        }

        if (!Strings.isNullOrEmpty(defaultValue)) {
            sb.append(", \"defaultValue\": ");
            sb.append(Strings.doubleQuote(defaultValue));
        }

        if (!Strings.isNullOrEmpty(description)) {
            sb.append(", \"description\": ");
            String text = sanitizeDescription(description);
            sb.append(Strings.doubleQuote(text));
        }

        sb.append(" }");
        return sb.toString();
    }

    /**
     * Gets the JSon schema type.
     *
     * @param   type the java type
     * @return  the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(String type, boolean enumType) {
        if (enumType) {
            return "enum";
        }

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

    /**
     * Sanitizes the javadoc to removed invalid characters so it can be used as json description
     *
     * @param javadoc  the javadoc
     * @return the text that is valid as json
     */
    public static String sanitizeDescription(String javadoc) {
        // lets just use what java accepts as identifiers
        StringBuilder sb = new StringBuilder();

        // split into lines
        String[] lines = javadoc.split("\n");

        boolean first = true;
        for (String line : lines) {
            line = line.trim();

            // skip lines that are javadoc references
            if (line.startsWith("@")) {
                continue;
            }

            // remove all HTML tags
            line = line.replaceAll("<.*?>", "");

            // remove all inlined javadoc links
            line = line.replaceAll("\\{\\@\\w+\\s(\\w+)\\}", "$1");

            // we are starting from a new line, so add a whitespace
            if (!first) {
                sb.append(' ');
            }

            for (char c : line.toCharArray()) {
                if (Character.isJavaIdentifierPart(c) || VALID_CHARS.indexOf(c) != -1) {
                    sb.append(c);
                } else if (Character.isWhitespace(c)) {
                    // always use space as whitespace, also for line feeds etc
                    sb.append(' ');
                }
            }

            first = false;
        }

        // remove double whitespaces, and trim
        String s = sb.toString();
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

}
