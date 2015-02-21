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
package org.apache.camel.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.PatternSyntaxException;

/**
 * Default {@link CamelCatalog}.
 */
public class DefaultCamelCatalog implements CamelCatalog {

    private static final String MODELS_CATALOG = "org/apache/camel/catalog/models.properties";
    private static final String COMPONENTS_CATALOG = "org/apache/camel/catalog/components.properties";
    private static final String DATA_FORMATS_CATALOG = "org/apache/camel/catalog/dataformats.properties";
    private static final String LANGUAGE_CATALOG = "org/apache/camel/catalog/languages.properties";
    private static final String MODEL_JSON = "org/apache/camel/catalog/models";
    private static final String COMPONENTS_JSON = "org/apache/camel/catalog/components";
    private static final String DATA_FORMATS_JSON = "org/apache/camel/catalog/dataformats";
    private static final String LANGUAGE_JSON = "org/apache/camel/catalog/languages";
    private static final String ARCHETYPES_CATALOG = "org/apache/camel/catalog/archetypes/archetype-catalog.xml";
    private static final String SCHEMAS_XML = "org/apache/camel/catalog/schemas";

    @Override
    public List<String> findComponentNames() {
        List<String> names = new ArrayList<String>();

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(COMPONENTS_CATALOG);
        if (is != null) {
            try {
                loadLines(is, names);
            } catch (IOException e) {
                // ignore
            }
        }
        return names;
    }

    @Override
    public List<String> findDataFormatNames() {
        List<String> names = new ArrayList<String>();

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(DATA_FORMATS_CATALOG);
        if (is != null) {
            try {
                loadLines(is, names);
            } catch (IOException e) {
                // ignore
            }
        }
        return names;
    }

    @Override
    public List<String> findLanguageNames() {
        List<String> names = new ArrayList<String>();

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(LANGUAGE_CATALOG);
        if (is != null) {
            try {
                loadLines(is, names);
            } catch (IOException e) {
                // ignore
            }
        }
        return names;
    }

    @Override
    public List<String> findModelNames() {
        List<String> names = new ArrayList<String>();

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(MODELS_CATALOG);
        if (is != null) {
            try {
                loadLines(is, names);
            } catch (IOException e) {
                // ignore
            }
        }
        return names;
    }

    @Override
    public List<String> findModelNames(String filter) {
        List<String> answer = new ArrayList<String>();

        List<String> names = findModelNames();
        for (String name : names) {
            String json = modelJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            try {
                                if (part.equalsIgnoreCase(filter) || matchWildcard(part, filter) || part.matches(filter)) {
                                    answer.add(name);
                                }
                            } catch (PatternSyntaxException e) {
                                // ignore as filter is maybe not a pattern
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public List<String> findComponentNames(String filter) {
        List<String> answer = new ArrayList<String>();

        List<String> names = findComponentNames();
        for (String name : names) {
            String json = componentJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            try {
                                if (part.equalsIgnoreCase(filter) || matchWildcard(part, filter) || part.matches(filter)) {
                                    answer.add(name);
                                }
                            } catch (PatternSyntaxException e) {
                                // ignore as filter is maybe not a pattern
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public List<String> findDataFormatNames(String filter) {
        List<String> answer = new ArrayList<String>();

        List<String> names = findDataFormatNames();
        for (String name : names) {
            String json = dataFormatJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            try {
                                if (part.equalsIgnoreCase(filter) || matchWildcard(part, filter) || part.matches(filter)) {
                                    answer.add(name);
                                }
                            } catch (PatternSyntaxException e) {
                                // ignore as filter is maybe not a pattern
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public List<String> findLanguageNames(String filter) {
        List<String> answer = new ArrayList<String>();

        List<String> names = findLanguageNames();
        for (String name : names) {
            String json = languageJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            try {
                                if (part.equalsIgnoreCase(filter) || matchWildcard(part, filter) || part.matches(filter)) {
                                    answer.add(name);
                                }
                            } catch (PatternSyntaxException e) {
                                // ignore as filter is maybe not a pattern
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public String modelJSonSchema(String name) {
        String file = MODEL_JSON + "/" + name + ".json";

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public String componentJSonSchema(String name) {
        String file = COMPONENTS_JSON + "/" + name + ".json";

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public String dataFormatJSonSchema(String name) {
        String file = DATA_FORMATS_JSON + "/" + name + ".json";

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public String languageJSonSchema(String name) {
        String file = LANGUAGE_JSON + "/" + name + ".json";

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public Set<String> findModelLabels() {
        SortedSet<String> answer = new TreeSet<String>();

        List<String> names = findModelNames();
        for (String name : names) {
            String json = modelJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            answer.add(part);
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public Set<String> findComponentLabels() {
        SortedSet<String> answer = new TreeSet<String>();

        List<String> names = findComponentNames();
        for (String name : names) {
            String json = componentJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            answer.add(part);
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public Set<String> findDataFormatLabels() {
        SortedSet<String> answer = new TreeSet<String>();

        List<String> names = findDataFormatNames();
        for (String name : names) {
            String json = dataFormatJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            answer.add(part);
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public Set<String> findLanguageLabels() {
        SortedSet<String> answer = new TreeSet<String>();

        List<String> names = findLanguageNames();
        for (String name : names) {
            String json = languageJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("label")) {
                        String label = row.get("label");
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            answer.add(part);
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public String archetypeCatalogAsXml() {
        String file = ARCHETYPES_CATALOG;

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public String springSchemaAsXml() {
        String file = SCHEMAS_XML + "/camel-spring.xsd";

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public String blueprintSchemaAsXml() {
        String file = SCHEMAS_XML + "/camel-blueprint.xsd";

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(file);
        if (is != null) {
            try {
                return loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

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
    private static void loadLines(InputStream in, List<String> lines) throws IOException {
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new LineNumberReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    lines.add(line);
                } else {
                    break;
                }
            }
        } finally {
            isr.close();
            in.close();
        }
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new LineNumberReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            isr.close();
            in.close();
        }
    }

    private static boolean matchWildcard(String name, String pattern) {
        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }
        return false;
    }

}
