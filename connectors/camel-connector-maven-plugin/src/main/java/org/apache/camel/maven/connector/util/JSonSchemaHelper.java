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
package org.apache.camel.maven.connector.util;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Set;

import org.apache.camel.json.simple.Jsoner;

/**
 * A helper class for <a href="http://json-schema.org/">JSON schema</a>.
 */
public final class JSonSchemaHelper {

    private static final String VALID_CHARS = ".-='/\\!&():;";

    private JSonSchemaHelper() {
    }

    public static String toJson(String name, String displayName, String kind, Boolean required, String type, String defaultValue, String description,
                                Boolean deprecated, Boolean secret, String group, String label, boolean enumType, Set<String> enums,
                                boolean oneOfType, Set<String> oneOffTypes, boolean asPredicate, String optionalPrefix, String prefix, boolean multiValue) {
        String typeName = getType(type, enumType);

        StringBuilder sb = new StringBuilder();
        sb.append(StringHelper.doubleQuote(name));
        sb.append(": { \"kind\": ");
        sb.append(StringHelper.doubleQuote(kind));

        // compute a display name if we don't have anything
        if (StringHelper.isNullOrEmpty(displayName)) {
            displayName = StringHelper.asTitle(name);
        }
        // we want display name early so its easier to spot
        sb.append(", \"displayName\": ");
        sb.append(StringHelper.doubleQuote(displayName));

        // we want group early so its easier to spot
        if (!StringHelper.isNullOrEmpty(group)) {
            sb.append(", \"group\": ");
            sb.append(StringHelper.doubleQuote(group));
        }

        // we want label early so its easier to spot
        if (!StringHelper.isNullOrEmpty(label)) {
            sb.append(", \"label\": ");
            sb.append(StringHelper.doubleQuote(label));
        }

        if (required != null) {
            // boolean type
            sb.append(", \"required\": ");
            sb.append(required.toString());
        }

        sb.append(", \"type\": ");
        if ("enum".equals(typeName)) {
            String actualType = getType(type, false);
            sb.append(StringHelper.doubleQuote(actualType));
            sb.append(", \"javaType\": \"" + type + "\"");
            CollectionStringBuffer enumValues = new CollectionStringBuffer();
            for (Object value : enums) {
                enumValues.append(StringHelper.doubleQuote(value.toString()));
            }
            sb.append(", \"enum\": [ ");
            sb.append(enumValues.toString());
            sb.append(" ]");
        } else if (oneOfType) {
            sb.append(StringHelper.doubleQuote(typeName));
            sb.append(", \"javaType\": \"" + type + "\"");
            CollectionStringBuffer oneOfValues = new CollectionStringBuffer();
            for (Object value : oneOffTypes) {
                oneOfValues.append(StringHelper.doubleQuote(value.toString()));
            }
            sb.append(", \"oneOf\": [ ");
            sb.append(oneOfValues.toString());
            sb.append(" ]");
        } else if ("array".equals(typeName)) {
            sb.append(StringHelper.doubleQuote("array"));
            sb.append(", \"javaType\": \"" + type + "\"");
        } else {
            sb.append(StringHelper.doubleQuote(typeName));
            sb.append(", \"javaType\": \"" + type + "\"");
        }

        if (!StringHelper.isNullOrEmpty(optionalPrefix)) {
            sb.append(", \"optionalPrefix\": ");
            String text = safeDefaultValue(optionalPrefix);
            sb.append(StringHelper.doubleQuote(text));
        }

        if (!StringHelper.isNullOrEmpty(prefix)) {
            sb.append(", \"prefix\": ");
            String text = safeDefaultValue(prefix);
            sb.append(StringHelper.doubleQuote(text));
        }
        if (multiValue) {
            // boolean value
            sb.append(", \"multiValue\": true");
        }

        if (deprecated != null) {
            sb.append(", \"deprecated\": ");
            // boolean value
            sb.append(deprecated.toString());
        }

        if (secret != null) {
            sb.append(", \"secret\": ");
            // boolean value
            sb.append(secret.toString());
        }

        if (!StringHelper.isNullOrEmpty(defaultValue)) {
            sb.append(", \"defaultValue\": ");
            String text = safeDefaultValue(defaultValue);
            // the type can either be boolean, integer, number or text based
            if ("boolean".equals(typeName) || "integer".equals(typeName) || "number".equals(typeName)) {
                sb.append(text);
            } else {
                // text should be quoted
                sb.append(StringHelper.doubleQuote(text));
            }
        }

        // for expressions we want to know if it must be used as predicate or not
        boolean predicate = "expression".equals(kind) || asPredicate;
        if (predicate) {
            sb.append(", \"asPredicate\": ");
            if (asPredicate) {
                sb.append("true");
            } else {
                sb.append("false");
            }
        }

        if (!StringHelper.isNullOrEmpty(description)) {
            sb.append(", \"description\": ");
            String text = sanitizeDescription(description, false);
            sb.append(StringHelper.doubleQuote(text));
        }

        sb.append(" }");

        return sb.toString();
    }

    /**
     * Pretty print the json.
     *
     * @param json existing json
     * @return output in pretty printed format
     */
    public static String prettyPrint(String json) {
        return Jsoner.prettyPrint(json, 2);
    }

    /**
     * Gets the JSon schema type.
     *
     * @param   type the java type
     * @return the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(String type, boolean enumType) {
        if (enumType) {
            return "enum";
        } else if (type == null) {
            // return generic type for unknown type
            return "object";
        } else if (type.equals(URI.class.getName()) || type.equals(URL.class.getName())) {
            return "string";
        } else if (type.equals(File.class.getName())) {
            return "string";
        } else if (type.equals(Date.class.getName())) {
            return "string";
        } else if (type.startsWith("java.lang.Class")) {
            return "string";
        } else if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection")) {
            return "array";
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
     * @return the json schema primitive type, or <tt>null</tt> if not a primitive
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
        } else if ("java.lang.Character".equals(name) || "Character".equals(name) || "char".equals(name)) {
            return "string";
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return "string";
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name) || "boolean".equals(name)) {
            return "boolean";
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name) || "int".equals(name)) {
            return "integer";
        } else if ("java.lang.Long".equals(name) || "Long".equals(name) || "long".equals(name)) {
            return "integer";
        } else if ("java.lang.Short".equals(name) || "Short".equals(name) || "short".equals(name)) {
            return "integer";
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name) || "byte".equals(name)) {
            return "integer";
        } else if ("java.lang.Float".equals(name) || "Float".equals(name) || "float".equals(name)) {
            return "number";
        } else if ("java.lang.Double".equals(name) || "Double".equals(name) || "double".equals(name)) {
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
    public static String sanitizeDescription(String javadoc, boolean summary) {
        if (StringHelper.isNullOrEmpty(javadoc)) {
            return null;
        }

        // lets just use what java accepts as identifiers
        StringBuilder sb = new StringBuilder();

        // split into lines
        String[] lines = javadoc.split("\n");

        boolean first = true;
        for (String line : lines) {
            line = line.trim();

            // terminate if we reach @param, @return or @deprecated as we only want the javadoc summary
            if (line.startsWith("@param") || line.startsWith("@return") || line.startsWith("@deprecated")) {
                break;
            }

            // skip lines that are javadoc references
            if (line.startsWith("@")) {
                continue;
            }

            // remove all XML tags
            line = line.replaceAll("<.*?>", "");

            // remove all inlined javadoc links, eg such as {@link org.apache.camel.spi.Registry}
            line = line.replaceAll("\\{\\@\\w+\\s([\\w.]+)\\}", "$1");

            // we are starting from a new line, so add a whitespace
            if (!first) {
                sb.append(' ');
            }

            // create a new line
            StringBuilder cb = new StringBuilder();
            for (char c : line.toCharArray()) {
                if (Character.isJavaIdentifierPart(c) || VALID_CHARS.indexOf(c) != -1) {
                    cb.append(c);
                } else if (Character.isWhitespace(c)) {
                    // always use space as whitespace, also for line feeds etc
                    cb.append(' ');
                }
            }

            // append data
            String s = cb.toString().trim();
            sb.append(s);

            boolean empty = StringHelper.isNullOrEmpty(s);
            boolean endWithDot = s.endsWith(".");
            boolean haveText = sb.length() > 0;

            if (haveText && summary && (empty || endWithDot)) {
                // if we only want a summary, then skip at first empty line we encounter, or if the sentence ends with a dot
                break;
            }

            first = false;
        }

        // remove double whitespaces, and trim
        String s = sb.toString();
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    /**
     * The default value may need to be escaped to be safe for json
     */
    private static String safeDefaultValue(String value) {
        if ("\"".equals(value)) {
            return "\\\"";
        } else if ("\\".equals(value)) {
            return "\\\\";
        } else {
            return value;
        }
    }

}
