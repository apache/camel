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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The component models of the catalog for camel catalog component, read from the first object of their JSON only
 * ({@code "component"}), which has the name, title, support level, description, label and Maven coordinates that the
 * list shows. The options and headers that follow are most of the JSON (the 422 component models are 14 MB), and
 * reading and parsing all of it made camel catalog component take 400 ms. The models of data formats, languages and
 * others are small: reading the first object first costs more than it saves.
 * <p>
 * When the JSON is not in the catalog (such as a component added to it at runtime) or does not start with that object,
 * the whole model is read from the catalog instead.
 */
final class CatalogModelHeader {

    private static final String EXTENSION = ".json";

    private CatalogModelHeader() {
    }

    // no lambdas or method references here: the first of each in a JVM costs milliseconds, about what a list saves

    static ComponentModel componentModel(CamelCatalog catalog, String name) {
        JsonObject json = read(catalog, catalog.getRuntimeProvider().getComponentJSonSchemaDirectory(), name, "component");
        try {
            if (json != null) {
                return JsonMapper.generateComponentModel(json);
            }
        } catch (RuntimeException e) {
            // the catalog reads the whole model
        }
        return catalog.componentModel(name);
    }

    /**
     * The first object of the JSON of the model, as a JSON object of its own, or {@code null} when the JSON is not in
     * the catalog or does not start with that object.
     */
    private static JsonObject read(CamelCatalog catalog, String directory, String name, String key) {
        if (directory == null) {
            return null;
        }
        try (InputStream is = catalog.getVersionManager().getResourceAsStream(directory + "/" + name + EXTENSION)) {
            if (is == null) {
                return null;
            }
            String json = firstObject(new InputStreamReader(is, StandardCharsets.UTF_8), key);
            return json != null ? (JsonObject) Jsoner.deserialize(json) : null;
        } catch (Exception e) {
            // not the expected JSON: the catalog reads the whole model
            return null;
        }
    }

    /**
     * Reads a JSON object up to the end of the value of its first key, when that key is the given one and its value is
     * an object, and returns that part as a JSON object of its own: {@code {"key": {...}}}.
     *
     * @param  reader the JSON
     * @param  key    the first key
     * @return        the JSON of the first key, or {@code null} when the JSON does not start with that key and an
     *                object
     */
    static String firstObject(Reader reader, String key) throws IOException {
        Text text = new Text(reader);
        if (text.nextNonBlank() != '{' || text.nextNonBlank() != '"') {
            return null;
        }
        for (int i = 0; i < key.length(); i++) {
            if (text.next() != key.charAt(i)) {
                return null;
            }
        }
        if (text.next() != '"' || text.nextNonBlank() != ':' || text.nextNonBlank() != '{') {
            return null;
        }
        int begin = text.pos - 1;
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;
        while (depth > 0) {
            int c = text.next();
            if (c == -1) {
                // the JSON ended before the object did
                return null;
            } else if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return "{\"" + key + "\":" + text.chars.substring(begin, text.pos) + "}";
    }

    /**
     * The characters read so far, read in chunks (one character at a time costs more than reading the whole JSON).
     */
    private static final class Text {
        private final Reader reader;
        private final char[] chunk = new char[4096];
        private final StringBuilder chars = new StringBuilder(4096);
        private int pos;

        Text(Reader reader) {
            this.reader = reader;
        }

        int next() throws IOException {
            while (pos >= chars.length()) {
                int n = reader.read(chunk);
                if (n == -1) {
                    return -1;
                }
                chars.append(chunk, 0, n);
            }
            return chars.charAt(pos++);
        }

        int nextNonBlank() throws IOException {
            int c = next();
            while (c != -1 && Character.isWhitespace(c)) {
                c = next();
            }
            return c;
        }
    }
}
