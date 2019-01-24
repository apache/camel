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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.apache.camel.tools.apt.helper.JsonSchemaHelper.parseJsonSchema;

/**
 * Helper to find documentation for inherited options when a component extends another.
 */
public final class DocumentationHelper {

    private DocumentationHelper() {
        //utility class, never constructed
    }

    public static String findComponentJavaDoc(String scheme, String extendsScheme, String fieldName) {
        File file = jsonFile(scheme, extendsScheme);
        if (file != null) {
            try {
                String json = loadText(file);
                List<Map<String, String>> rows = parseJsonSchema("componentProperties", json, true);
                return getPropertyDescription(rows, fieldName);
            } catch (Exception e) {
                // ignore
            }
        }

        // not found
        return null;
    }

    public static String findEndpointJavaDoc(String scheme, String extendsScheme, String fieldName) {
        File file = jsonFile(scheme, extendsScheme);
        if (file != null) {
            try {
                String json = loadText(file);
                List<Map<String, String>> rows = parseJsonSchema("properties", json, true);
                return getPropertyDescription(rows, fieldName);
            } catch (Exception e) {
                // ignore
            }
        }

        // not found
        return null;
    }

    private static String getPropertyDescription(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String description = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("description")) {
                description = row.get("description");
            }
            if (found) {
                return description;
            }
        }
        return null;
    }

    private static File jsonFile(String scheme, String extendsScheme) {
        // we cannot use classloader to load external resources from other JARs during apt plugin,
        // so load these resources using the file system

        if ("file".equals(extendsScheme)) {
            return new File("../camel-file/target/classes/org/apache/camel/component/file/file.json");
        } else if ("ahc".equals(extendsScheme)) {
            return new File("../camel-ahc/target/classes/org/apache/camel/component/ahc/ahc.json");
        } else if ("atom".equals(extendsScheme)) {
            return new File("../camel-atom/target/classes/org/apache/camel/component/atom/atom.json");
        } else if ("ftp".equals(extendsScheme)) {
            return new File("../camel-ftp/target/classes/org/apache/camel/component/file/remote/ftp.json");
        } else if ("jms".equals(extendsScheme)) {
            return new File("../camel-jms/target/classes/org/apache/camel/component/jms/jms.json");
        } else if ("sjms".equals(extendsScheme)) {
            return new File("../camel-sjms/target/classes/org/apache/camel/component/sjms/sjms.json");
        } else if ("http".equals(extendsScheme)) {
            return new File("../camel-http/target/classes/org/apache/camel/component/http/http.json");
        } else if ("https".equals(extendsScheme)) {
            return new File("../camel-http/target/classes/org/apache/camel/component/http/https.json");
        } else if ("netty".equals(extendsScheme)) {
            return new File("../camel-netty/target/classes/org/apache/camel/component/netty/netty.json");
        } else if ("netty4".equals(extendsScheme)) {
            return new File("../camel-netty4/target/classes/org/apache/camel/component/netty4/netty4.json");
        } else if ("servlet".equals(extendsScheme)) {
            return new File("../camel-servlet/target/classes/org/apache/camel/component/servlet/servlet.json");
        }
        // not found
        return null;
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    private static String loadText(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        Files.readAllLines(file.toPath()).forEach(line -> {
            builder.append(line);
            builder.append("\n");
        });
        return builder.toString();
    }

}
