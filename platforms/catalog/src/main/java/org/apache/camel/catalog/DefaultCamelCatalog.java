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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.apache.camel.catalog.URISupport.createQueryString;

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

    private static final Pattern SYNTAX_PATTERN = Pattern.compile("(\\w+)");

    @Override
    public List<String> findComponentNames() {
        List<String> names = new ArrayList<String>();

        InputStream is = DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(COMPONENTS_CATALOG);
        if (is != null) {
            try {
                CatalogHelper.loadLines(is, names);
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
                CatalogHelper.loadLines(is, names);
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
                CatalogHelper.loadLines(is, names);
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
                CatalogHelper.loadLines(is, names);
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
                                if (part.equalsIgnoreCase(filter) || CatalogHelper.matchWildcard(part, filter) || part.matches(filter)) {
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
                                if (part.equalsIgnoreCase(filter) || CatalogHelper.matchWildcard(part, filter) || part.matches(filter)) {
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
                                if (part.equalsIgnoreCase(filter) || CatalogHelper.matchWildcard(part, filter) || part.matches(filter)) {
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
                                if (part.equalsIgnoreCase(filter) || CatalogHelper.matchWildcard(part, filter) || part.matches(filter)) {
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
                return CatalogHelper.loadText(is);
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
                return CatalogHelper.loadText(is);
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
                return CatalogHelper.loadText(is);
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
                return CatalogHelper.loadText(is);
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
                return CatalogHelper.loadText(is);
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
                return CatalogHelper.loadText(is);
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
                return CatalogHelper.loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public Map<String, String> endpointProperties(String uri) throws URISyntaxException {
        // parse the uri
        URI u = new URI(uri);
        String scheme = u.getScheme();

        String json = componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }

        // grab the syntax
        String syntax = null;
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : rows) {
            if (row.containsKey("syntax")) {
                syntax = row.get("syntax");
                break;
            }
        }
        if (syntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // grab the json schema for the endpoint properties
       // rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        // now parse the uri to know which part isw what
        Map<String, String> answer = new LinkedHashMap<String, String>();

        // parse the syntax and find the same group in the uri

        Matcher matcher = SYNTAX_PATTERN.matcher(syntax);
        Matcher matcher2 = SYNTAX_PATTERN.matcher(uri);
        while (matcher.find() && matcher2.find()) {
            String word = matcher.group(1);
            String word2 = matcher2.group(1);
            // skip the scheme as we know that already
            if (!scheme.equals(word)) {
                answer.put(word, word2);
            }
        }

        // now parse the uri parameters
        Map<String, Object> parameters = URISupport.parseParameters(u);

        // and covert the values to String so its JMX friendly
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            answer.put(key, value);
        }

        return answer;
    }

    @Override
    public String asEndpointUri(String scheme, String json) throws URISyntaxException {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        Map<String, String> copy = new HashMap<String, String>();
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            String required = row.get("required");
            String value = row.get("value");
            String defaultValue = row.get("defaultValue");

            // only add if either required, or the value is != default value
            String valueToAdd = null;
            if ("true".equals(required)) {
                valueToAdd = value != null ? value : defaultValue;
                if (valueToAdd == null) {
                    valueToAdd = "";
                }
            } else {
                // if we have a value and no default then add it
                if (value != null && defaultValue == null) {
                    valueToAdd = value;
                }
                // otherwise only add if the value is != default value
                if (value != null && defaultValue != null && !value.equals(defaultValue)) {
                    valueToAdd = value;
                }
            }

            if (valueToAdd != null) {
                copy.put(name, valueToAdd);
            }
        }

        return asEndpointUri(scheme, copy);
    }

    @Override
    public String asEndpointUri(String scheme, Map<String, String> properties) throws URISyntaxException {
        String json = componentJSonSchema(scheme);
        if (json == null) {
            throw new IllegalArgumentException("Cannot find endpoint with scheme " + scheme);
        }

        // grab the syntax
        String syntax = null;
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        for (Map<String, String> row : rows) {
            if (row.containsKey("syntax")) {
                syntax = row.get("syntax");
                break;
            }
        }
        if (syntax == null) {
            throw new IllegalArgumentException("Endpoint with scheme " + scheme + " has no syntax defined in the json schema");
        }

        // build at first according to syntax
        Map<String, String> copy = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            if (syntax.contains(key)) {
                syntax = syntax.replace(key, value);
            } else {
                copy.put(key, value);
            }
        }

        StringBuilder sb = new StringBuilder(syntax);
        if (!copy.isEmpty()) {
            sb.append('?');
            String query = createQueryString(copy);
            sb.append(query);
        }

        return sb.toString();
    }

}
