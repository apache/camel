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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import static org.apache.camel.catalog.CatalogHelper.after;
import static org.apache.camel.catalog.JSonSchemaHelper.getPropertyDefaultValue;
import static org.apache.camel.catalog.JSonSchemaHelper.isPropertyRequired;
import static org.apache.camel.catalog.URISupport.createQueryString;
import static org.apache.camel.catalog.URISupport.stripQuery;

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

    private final VersionHelper version = new VersionHelper();

    @Override
    public String getCatalogVersion() {
        return version.getVersion();
    }

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
        // NOTICE: This logic is similar to org.apache.camel.util.EndpointHelper#endpointProperties
        // as the catalog also offers similar functionality (without having camel-core on classpath)

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

        // clip the scheme from the syntax
        syntax = after(syntax, ":");

        // parse the syntax and find the same group in the uri
        Matcher matcher = SYNTAX_PATTERN.matcher(syntax);
        List<String> word = new ArrayList<String>();
        while (matcher.find()) {
            String s = matcher.group(1);
            if (!scheme.equals(s)) {
                word.add(s);
            }
        }

        // clip the scheme from the uri
        uri = after(uri, ":");
        String uriPath = stripQuery(uri);

        // if there is only one, then use uriPath as is
        List<String> word2 = new ArrayList<String>();

        if (word.size() == 1) {
            String s = uriPath;
            s = URISupport.stripPrefix(s, scheme);
            // strip any leading : or / after the scheme
            while (s.startsWith(":") || s.startsWith("/")) {
                s = s.substring(1);
            }
            word2.add(s);
        } else {
            Matcher matcher2 = SYNTAX_PATTERN.matcher(uriPath);
            while (matcher2.find()) {
                String s = matcher2.group(1);
                if (!scheme.equals(s)) {
                    word2.add(s);
                }
            }
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

        boolean defaultValueAdded = false;

        // now parse the uri to know which part isw what
        Map<String, String> options = new LinkedHashMap<String, String>();

        // word contains the syntax path elements
        Iterator<String> it = word2.iterator();
        for (int i = 0; i < word.size(); i++) {
            String key = word.get(i);

            boolean allOptions = word.size() == word2.size();
            boolean required = isPropertyRequired(rows, key);
            String defaultValue = getPropertyDefaultValue(rows, key);

            // we have all options so no problem
            if (allOptions) {
                String value = it.next();
                options.put(key, value);
            } else {
                // we have a little problem as we do not not have all options
                if (!required) {
                    String value = defaultValue;
                    options.put(key, value);
                    defaultValueAdded = true;
                } else {
                    String value = it.next();
                    options.put(key, value);
                }
            }
        }

        Map<String, String> answer = new LinkedHashMap<String, String>();

        // remove all options which are using default values and are not required
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (defaultValueAdded) {
                boolean required = isPropertyRequired(rows, key);
                String defaultValue = getPropertyDefaultValue(rows, key);

                if (!required && defaultValue != null) {
                    if (defaultValue.equals(value)) {
                        continue;
                    }
                }
            }

            // we should keep this in the answer
            answer.put(key, value);
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
    public String endpointComponentName(String uri) {
        if (uri != null) {
            int idx = uri.indexOf(":");
            if (idx > 0) {
                return uri.substring(0, idx);
            }
        }
        return null;
    }

    @Override
    public String asEndpointUri(String scheme, String json) throws URISyntaxException {
        return doAsEndpointUri(scheme, json, "&");
    }

    @Override
    public String asEndpointUriXml(String scheme, String json) throws URISyntaxException {
        return doAsEndpointUri(scheme, json, "&amp;");
    }

    private String doAsEndpointUri(String scheme, String json, String ampersand) throws URISyntaxException {
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

        return doAsEndpointUri(scheme, copy, ampersand);
    }

    @Override
    public String asEndpointUri(String scheme, Map<String, String> properties) throws URISyntaxException {
        return doAsEndpointUri(scheme, properties, "&");
    }

    @Override
    public String asEndpointUriXml(String scheme, Map<String, String> properties) throws URISyntaxException {
        return doAsEndpointUri(scheme, properties, "&amp;");
    }

    private String doAsEndpointUri(String scheme, Map<String, String> properties, String ampersand) throws URISyntaxException {
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

        // clip the scheme from the syntax
        syntax = after(syntax, ":");

        String originalSyntax = syntax;

        // build at first according to syntax (use a tree map as we want the uri options sorted)
        Map<String, String> copy = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            if (syntax.contains(key)) {
                syntax = syntax.replace(key, value);
            } else {
                copy.put(key, value);
            }
        }

        // the tokens between the options in the path
        String[] tokens = syntax.split("\\w+");

        // parse the syntax into each options
        Matcher matcher = SYNTAX_PATTERN.matcher(originalSyntax);
        List<String> options = new ArrayList<String>();
        while (matcher.find()) {
            String s = matcher.group(1);
            options.add(s);
        }

        // parse the syntax into each options
        Matcher matcher2 = SYNTAX_PATTERN.matcher(syntax);
        List<String> options2 = new ArrayList<String>();
        while (matcher2.find()) {
            String s = matcher2.group(1);
            options2.add(s);
        }

        // build the endpoint
        StringBuilder sb = new StringBuilder();
        sb.append(scheme);
        sb.append(":");

        int range = 0;
        boolean first = true;
        boolean hasQuestionmark = false;
        for (int i = 0; i < options.size(); i++) {
            String key = options.get(i);
            String key2 = options2.get(i);
            String token = null;
            if (tokens.length > i) {
                token = tokens[i];
            }

            // was the option provided?
            if (properties.containsKey(key)) {
                if (!first && token != null) {
                    sb.append(token);
                }
                hasQuestionmark |= key.contains("?") || (token != null && token.contains("?"));
                sb.append(key2);
                first = false;
            }
            range++;
        }
        // append any extra options that was in surplus for the last
        while (range < options2.size()) {
            String token = null;
            if (tokens.length > range) {
                token = tokens[range];
            }
            String key2 = options2.get(range);
            sb.append(token);
            sb.append(key2);
            hasQuestionmark |= key2.contains("?") || (token != null && token.contains("?"));
            range++;
        }

        if (!copy.isEmpty()) {
            // the last option may already contain a ? char, if so we should use & instead of ?
            sb.append(hasQuestionmark ? ampersand : '?');
            String query = createQueryString(copy, ampersand);
            // we do not want to use %23 for # syntax
            query = query.replaceAll("\\=\\%23", "=#");
            sb.append(query);
        }

        return sb.toString();
    }

    @Override
    public String listComponentsAsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        List<String> names = findComponentNames();
        for (int i = 0; i < names.size(); i++) {
            String scheme = names.get(i);
            String json = componentJSonSchema(scheme);
            // skip first line
            json = CatalogHelper.between(json, "\"component\": {", "\"componentProperties\": {");
            json = json.trim();
            // skip last comma if not the last
            if (i == names.size() - 1) {
                json = json.substring(0, json.length() - 1);
            }
            sb.append("\n");
            sb.append("  {\n");
            sb.append("    ");
            sb.append(json);
        }

        sb.append("\n]");
        return sb.toString();
    }

    @Override
    public String listDataFormatsAsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        List<String> names = findDataFormatNames();
        for (int i = 0; i < names.size(); i++) {
            String scheme = names.get(i);
            String json = dataFormatJSonSchema(scheme);
            // skip first line
            json = CatalogHelper.between(json, "\"dataformat\": {", "\"properties\": {");
            json = json.trim();
            // skip last comma if not the last
            if (i == names.size() - 1) {
                json = json.substring(0, json.length() - 1);
            }
            sb.append("\n");
            sb.append("  {\n");
            sb.append("    ");
            sb.append(json);
        }

        sb.append("\n]");
        return sb.toString();
    }

    @Override
    public String listLanguagesAsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        List<String> names = findLanguageNames();
        for (int i = 0; i < names.size(); i++) {
            String scheme = names.get(i);
            String json = languageJSonSchema(scheme);
            // skip first line
            json = CatalogHelper.between(json, "\"language\": {", "\"properties\": {");
            json = json.trim();
            // skip last comma if not the last
            if (i == names.size() - 1) {
                json = json.substring(0, json.length() - 1);
            }
            sb.append("\n");
            sb.append("  {\n");
            sb.append("    ");
            sb.append(json);
        }

        sb.append("\n]");
        return sb.toString();
    }

    @Override
    public String listModelsAsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        List<String> names = findModelNames();
        for (int i = 0; i < names.size(); i++) {
            String scheme = names.get(i);
            String json = modelJSonSchema(scheme);
            // skip first line
            json = CatalogHelper.between(json, "\"model\": {", "\"properties\": {");
            json = json.trim();
            // skip last comma if not the last
            if (i == names.size() - 1) {
                json = json.substring(0, json.length() - 1);
            }
            sb.append("\n");
            sb.append("  {\n");
            sb.append("    ");
            sb.append(json);
        }

        sb.append("\n]");
        return sb.toString();
    }

    @Override
    public String summaryAsJson() {
        int archetypes = 0;
        try {
            String xml = archetypeCatalogAsXml();
            Document dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));
            Object val = XPathFactory.newInstance().newXPath().evaluate("count(/archetype-catalog/archetypes/archetype)", dom, XPathConstants.NUMBER);
            double num = (double) val;
            archetypes = (int) num;
        } catch (Exception e) {
            // ignore
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": \"" + getCatalogVersion() + "\",\n");
        sb.append("  \"eips\": " + findModelNames().size() + ",\n");
        sb.append("  \"components\": " + findComponentNames().size() + ",\n");
        sb.append("  \"dataformats\": " + findDataFormatNames().size() + ",\n");
        sb.append("  \"languages\": " + findLanguageNames().size() + ",\n");
        sb.append("  \"archetypes\": " + archetypes + "\n");
        sb.append("}");
        return sb.toString();
    }
}
