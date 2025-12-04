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

package org.apache.camel.maven.packaging;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.project.MavenProject;

public final class SchemaHelper {

    private SchemaHelper() {}

    /**
     * Converts the string from dash format into camel case (hello-great-world -> helloGreatWorld)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String dashToCamelCase(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        if (text.indexOf('-') == -1) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-') {
                i++;
                sb.append(Character.toUpperCase(text.charAt(i)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts the string from camel case into dash format (helloGreatWorld -> hello-great-world)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String camelCaseToDash(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder answer = new StringBuilder(text.length());

        Character prev = null;
        Character next;
        char[] arr = text.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (i < arr.length - 1) {
                next = arr[i + 1];
            } else {
                next = null;
            }
            if (ch == '-' || ch == '_') {
                answer.append("-");
            } else if (Character.isUpperCase(ch) && prev != null && !Character.isUpperCase(prev)) {
                if (prev != '-' && prev != '_') {
                    answer.append("-");
                }
                answer.append(ch);
            } else if (Character.isUpperCase(ch) && prev != null && next != null && Character.isLowerCase(next)) {
                if (prev != '-' && prev != '_') {
                    answer.append("-");
                }
                answer.append(ch);
            } else {
                answer.append(ch);
            }
            prev = ch;
        }

        return answer.toString().toLowerCase(Locale.ENGLISH);
    }

    public static void addModelMetadata(BaseModel<?> model, Metadata metadata) {
        if (metadata == null) {
            return;
        }

        addModelMetadata(model, metadata.annotations());
    }

    public static void addModelMetadata(BaseModel<?> model, String key, String value) {
        Map<String, Object> modelMetadata = getModelMetadata(model);

        if (!Strings.isNullOrEmpty(key) && !Strings.isNullOrEmpty(value)) {
            modelMetadata.put(key, value);
        }
    }

    public static void addModelMetadata(BaseModel<?> model, MavenProject project) {
        if (project == null) {
            return;
        }

        String annotations = project.getProperties().getProperty("annotations");

        if (!Strings.isNullOrEmpty(annotations)) {
            addModelMetadata(model, annotations.split(","));
        }
    }

    public static void addModelMetadata(BaseModel<?> model, String[] annotations) {
        if (annotations == null) {
            return;
        }

        Map<String, Object> modelMetadata = getModelMetadata(model);

        for (String annotation : annotations) {
            String key = Strings.before(annotation, "=");
            String val = Strings.after(annotation, "=");

            if (!Strings.isNullOrEmpty(key) && !Strings.isNullOrEmpty(val)) {
                modelMetadata.put(key, val);
            }
        }
    }

    public static Map<String, Object> getModelMetadata(BaseModel<?> model) {
        Map<String, Object> meta = model.getMetadata();
        if (meta == null) {
            meta = new TreeMap<>();
            model.setMetadata(meta);
        }

        return meta;
    }
}
