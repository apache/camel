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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.PatternSyntaxException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

/**
 * Default {@link CamelCatalog}.
 */
public class DefaultCamelCatalog extends AbstractCamelCatalog implements CamelCatalog {

    private static final String MODELS_CATALOG = "org/apache/camel/catalog/models.properties";
    private static final String MODEL_DIR = "org/apache/camel/catalog/models";
    private static final String DOC_DIR = "org/apache/camel/catalog/docs";
    private static final String ARCHETYPES_CATALOG = "org/apache/camel/catalog/archetypes/archetype-catalog.xml";
    private static final String SCHEMAS_XML = "org/apache/camel/catalog/schemas";

    private final VersionHelper version = new VersionHelper();

    // 3rd party components/data-formats
    private final Map<String, String> extraComponents = new HashMap<String, String>();
    private final Map<String, String> extraComponentsJSonSchema = new HashMap<String, String>();
    private final Map<String, String> extraDataFormats = new HashMap<String, String>();
    private final Map<String, String> extraDataFormatsJSonSchema = new HashMap<String, String>();

    // cache of operation -> result
    private final Map<String, Object> cache = new HashMap<String, Object>();

    private boolean caching;
    private VersionManager versionManager = new DefaultVersionManager(this);
    private RuntimeProvider runtimeProvider = new DefaultRuntimeProvider(this);

    /**
     * Creates the {@link CamelCatalog} without caching enabled.
     */
    public DefaultCamelCatalog() {
        this(false);
    }

    /**
     * Creates the {@link CamelCatalog}
     *
     * @param caching  whether to use cache
     */
    public DefaultCamelCatalog(boolean caching) {
        this.caching = caching;
        setJSonSchemaResolver(new CamelCatalogJSonSchemaResolver(this, extraComponents, extraComponentsJSonSchema, extraDataFormats, extraDataFormatsJSonSchema));
    }

    @Override
    public RuntimeProvider getRuntimeProvider() {
        return runtimeProvider;
    }

    @Override
    public void setRuntimeProvider(RuntimeProvider runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
        // inject CamelCatalog to the provider
        this.runtimeProvider.setCamelCatalog(this);
        // invalidate the cache
        cache.remove("findComponentNames");
        cache.remove("listComponentsAsJson");
        cache.remove("findDataFormatNames");
        cache.remove("listDataFormatsAsJson");
        cache.remove("findLanguageNames");
        cache.remove("listLanguagesAsJson");
    }

    @Override
    public void enableCache() {
        caching = true;
    }

    @Override
    public boolean isCaching() {
        return caching;
    }

    @Override
    public void setVersionManager(VersionManager versionManager) {
        this.versionManager = versionManager;
    }

    @Override
    public VersionManager getVersionManager() {
        return versionManager;
    }

    @Override
    public void addComponent(String name, String className) {
        extraComponents.put(name, className);
        // invalidate the cache
        cache.remove("findComponentNames");
        cache.remove("findComponentLabels");
        cache.remove("listComponentsAsJson");
    }

    @Override
    public void addComponent(String name, String className, String jsonSchema) {
        addComponent(name, className);
        if (jsonSchema != null) {
            extraComponentsJSonSchema.put(name, jsonSchema);
        }
    }

    @Override
    public void addDataFormat(String name, String className) {
        extraDataFormats.put(name, className);
        // invalidate the cache
        cache.remove("findDataFormatNames");
        cache.remove("findDataFormatLabels");
        cache.remove("listDataFormatsAsJson");
    }

    @Override
    public void addDataFormat(String name, String className, String jsonSchema) {
        addDataFormat(name, className);
        if (jsonSchema != null) {
            extraDataFormatsJSonSchema.put(name, jsonSchema);
        }
    }

    @Override
    public String getCatalogVersion() {
        return version.getVersion();
    }

    @Override
    public boolean loadVersion(String version) {
        if (version.equals(versionManager.getLoadedVersion())) {
            return true;
        } else if (versionManager.loadVersion(version)) {
            // invalidate existing cache if we loaded a new version
            cache.clear();
            return true;
        }
        return false;
    }

    @Override
    public String getLoadedVersion() {
        return versionManager.getLoadedVersion();
    }

    @Override
    public String getRuntimeProviderLoadedVersion() {
        return versionManager.getRuntimeProviderLoadedVersion();
    }

    @Override
    public boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version) {
        return versionManager.loadRuntimeProviderVersion(groupId, artifactId, version);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findComponentNames() {
        List<String> names = null;
        if (caching) {
            names = (List<String>) cache.get("findComponentNames");
        }

        if (names == null) {
            names = runtimeProvider.findComponentNames();

            // include third party components
            for (Map.Entry<String, String> entry : extraComponents.entrySet()) {
                names.add(entry.getKey());
            }
            // sort the names
            Collections.sort(names);

            if (caching) {
                cache.put("findComponentNames", names);
            }
        }
        return names;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findDataFormatNames() {
        List<String> names = null;
        if (caching) {
            names = (List<String>) cache.get("findDataFormatNames");
        }

        if (names == null) {
            names = runtimeProvider.findDataFormatNames();

            // include third party data formats
            for (Map.Entry<String, String> entry : extraDataFormats.entrySet()) {
                names.add(entry.getKey());
            }
            // sort the names
            Collections.sort(names);

            if (caching) {
                cache.put("findDataFormatNames", names);
            }
        }
        return names;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findLanguageNames() {
        List<String> names = null;
        if (caching) {
            names = (List<String>) cache.get("findLanguageNames");
        }

        if (names == null) {
            names = runtimeProvider.findLanguageNames();

            if (caching) {
                cache.put("findLanguageNames", names);
            }
        }
        return names;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findModelNames() {
        List<String> names = null;
        if (caching) {
            names = (List<String>) cache.get("findModelNames");
        }

        if (names == null) {
            names = new ArrayList<String>();
            InputStream is = versionManager.getResourceAsStream(MODELS_CATALOG);
            if (is != null) {
                try {
                    CatalogHelper.loadLines(is, names);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("findModelNames", names);
            }
        }
        return names;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findOtherNames() {
        List<String> names = null;
        if (caching) {
            names = (List<String>) cache.get("findOtherNames");
        }

        if (names == null) {
            names = runtimeProvider.findOtherNames();

            if (caching) {
                cache.put("findOtherNames", names);
            }
        }
        return names;
    }

    @Override
    public List<String> findModelNames(String filter) {
        // should not cache when filter parameter can by any kind of value
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
        // should not cache when filter parameter can by any kind of value
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
        // should not cache when filter parameter can by any kind of value
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
        // should not cache when filter parameter can by any kind of value
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
    public List<String> findOtherNames(String filter) {
        // should not cache when filter parameter can by any kind of value
        List<String> answer = new ArrayList<String>();

        List<String> names = findOtherNames();
        for (String name : names) {
            String json = otherJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("other", json, false);
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
        String file = MODEL_DIR + "/" + name + ".json";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("model-" + file);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getModelJSonSchema(name);
            if (caching) {
                cache.put("model-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String componentJSonSchema(String name) {
        String file = runtimeProvider.getComponentJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("component-" + file);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getComponentJSonSchema(name);
            if (caching) {
                cache.put("component-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String dataFormatJSonSchema(String name) {
        String file = runtimeProvider.getDataFormatJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("dataformat-" + file);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getDataFormatJSonSchema(name);
            if (caching) {
                cache.put("dataformat-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String languageJSonSchema(String name) {
        // if we try to look method then its in the bean.json file
        if ("method".equals(name)) {
            name = "bean";
        }

        String file = runtimeProvider.getLanguageJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("language-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("language-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String otherJSonSchema(String name) {
        String file = runtimeProvider.getOtherJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("other-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("other-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String componentAsciiDoc(String name) {
        String answer = doComponentAsciiDoc(name);
        if (answer == null) {
            // maybe the name is an alternative scheme name, and then we need to find the component that
            // has the name as alternative, and use the first scheme as the name to find the documentation
            List<String> names = findComponentNames();
            for (String alternative : names) {
                String schemes = getAlternativeComponentName(alternative);
                if (schemes != null && schemes.contains(name)) {
                    String first = schemes.split(",")[0];
                    return componentAsciiDoc(first);
                }
            }
        }
        return answer;
    }

    @Override
    public String componentHtmlDoc(String name) {
        String answer = doComponentHtmlDoc(name);
        if (answer == null) {
            // maybe the name is an alternative scheme name, and then we need to find the component that
            // has the name as alternative, and use the first scheme as the name to find the documentation
            List<String> names = findComponentNames();
            for (String alternative : names) {
                String schemes = getAlternativeComponentName(alternative);
                if (schemes != null && schemes.contains(name)) {
                    String first = schemes.split(",")[0];
                    return componentHtmlDoc(first);
                }
            }
        }
        return answer;
    }

    private String getAlternativeComponentName(String componentName) {
        String json = componentJSonSchema(componentName);
        if (json != null) {
            List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
            for (Map<String, String> row : rows) {
                if (row.containsKey("alternativeSchemes")) {
                    return row.get("alternativeSchemes");
                }
            }
        }
        return null;
    }

    private String doComponentAsciiDoc(String name) {
        // special for mail component
        if (name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp") || name.equals("smtps")) {
            name = "mail";
        }

        String file = DOC_DIR + "/" + name + "-component.adoc";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("component-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            } else {
                // its maybe a third party so try load it
                String className = extraComponents.get(name);
                if (className != null) {
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    packageName = packageName.replace('.', '/');
                    String path = packageName + "/" + name + "-component.adoc";
                    is = versionManager.getResourceAsStream(path);
                    if (is != null) {
                        try {
                            answer = CatalogHelper.loadText(is);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            if (caching) {
                cache.put("component-" + file, answer);
            }
        }

        return answer;
    }

    private String doComponentHtmlDoc(String name) {
        // special for mail component
        if (name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp") || name.equals("smtps")) {
            name = "mail";
        }

        String file = DOC_DIR + "/" + name + "-component.html";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("component-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            } else {
                // its maybe a third party so try load it
                String className = extraComponents.get(name);
                if (className != null) {
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    packageName = packageName.replace('.', '/');
                    String path = packageName + "/" + name + "-component.html";
                    is = versionManager.getResourceAsStream(path);
                    if (is != null) {
                        try {
                            answer = CatalogHelper.loadText(is);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            if (caching) {
                cache.put("component-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String dataFormatAsciiDoc(String name) {
        // special for some name data formats
        if (name.startsWith("bindy")) {
            name = "bindy";
        } else if (name.startsWith("univocity")) {
            name = "univocity";
        }

        String file = DOC_DIR + "/" + name + "-dataformat.adoc";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("dataformat-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            } else {
                // its maybe a third party so try load it
                String className = extraDataFormats.get(name);
                if (className != null) {
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    packageName = packageName.replace('.', '/');
                    String path = packageName + "/" + name + "-dataformat.adoc";
                    is = versionManager.getResourceAsStream(path);
                    if (is != null) {
                        try {
                            answer = CatalogHelper.loadText(is);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            if (caching) {
                cache.put("dataformat-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String dataFormatHtmlDoc(String name) {
        // special for some name data formats
        if (name.startsWith("bindy")) {
            name = "bindy";
        } else if (name.startsWith("univocity")) {
            name = "univocity";
        }

        String file = DOC_DIR + "/" + name + "-dataformat.html";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("dataformat-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            } else {
                // its maybe a third party so try load it
                String className = extraDataFormats.get(name);
                if (className != null) {
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    packageName = packageName.replace('.', '/');
                    String path = packageName + "/" + name + "-dataformat.html";
                    is = versionManager.getResourceAsStream(path);
                    if (is != null) {
                        try {
                            answer = CatalogHelper.loadText(is);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            if (caching) {
                cache.put("dataformat-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String languageAsciiDoc(String name) {
        // if we try to look method then its in the bean.adoc file
        if ("method".equals(name)) {
            name = "bean";
        }

        String file = DOC_DIR + "/" + name + "-language.adoc";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("language-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("language-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String languageHtmlDoc(String name) {
        // if we try to look method then its in the bean.html file
        if ("method".equals(name)) {
            name = "bean";
        }

        String file = DOC_DIR + "/" + name + "-language.html";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("language-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("language-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String otherAsciiDoc(String name) {
        String file = DOC_DIR + "/" + name + ".adoc";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("other-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("other-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    public String otherHtmlDoc(String name) {
        String file = DOC_DIR + "/" + name + "-other.html";

        String answer = null;
        if (caching) {
            answer = (String) cache.get("language-" + file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put("language-" + file, answer);
            }
        }

        return answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> findModelLabels() {
        SortedSet<String> answer = null;
        if (caching) {
            answer = (TreeSet<String>) cache.get("findModelLabels");
        }

        if (answer == null) {
            answer = new TreeSet<String>();
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
            if (caching) {
                cache.put("findModelLabels", answer);
            }
        }

        return answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> findComponentLabels() {
        SortedSet<String> answer = null;
        if (caching) {
            answer = (TreeSet<String>) cache.get("findComponentLabels");
        }

        if (answer == null) {
            answer = new TreeSet<String>();
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
            if (caching) {
                cache.put("findComponentLabels", answer);
            }
        }

        return answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> findDataFormatLabels() {
        SortedSet<String> answer = null;
        if (caching) {
            answer = (TreeSet<String>) cache.get("findDataFormatLabels");
        }

        if (answer == null) {
            answer = new TreeSet<String>();
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
            if (caching) {
                cache.put("findDataFormatLabels", answer);
            }
        }

        return answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> findLanguageLabels() {
        SortedSet<String> answer = null;
        if (caching) {
            answer = (TreeSet<String>) cache.get("findLanguageLabels");
        }

        if (answer == null) {
            answer = new TreeSet<String>();
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
            if (caching) {
                cache.put("findLanguageLabels", answer);
            }
        }

        return answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> findOtherLabels() {
        SortedSet<String> answer = null;
        if (caching) {
            answer = (TreeSet<String>) cache.get("findOtherLabels");
        }

        if (answer == null) {
            answer = new TreeSet<String>();
            List<String> names = findOtherNames();
            for (String name : names) {
                String json = otherJSonSchema(name);
                if (json != null) {
                    List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("other", json, false);
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
            if (caching) {
                cache.put("findOtherLabels", answer);
            }
        }

        return answer;
    }

    @Override
    public String archetypeCatalogAsXml() {
        String file = ARCHETYPES_CATALOG;

        String answer = null;
        if (caching) {
            answer = (String) cache.get(file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put(file, answer);
            }
        }

        return answer;
    }

    @Override
    public String springSchemaAsXml() {
        String file = SCHEMAS_XML + "/camel-spring.xsd";

        String answer = null;
        if (caching) {
            answer = (String) cache.get(file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put(file, answer);
            }
        }

        return answer;
    }

    @Override
    public String blueprintSchemaAsXml() {
        String file = SCHEMAS_XML + "/camel-blueprint.xsd";

        String answer = null;
        if (caching) {
            answer = (String) cache.get(file);
        }

        if (answer == null) {
            InputStream is = versionManager.getResourceAsStream(file);
            if (is != null) {
                try {
                    answer = CatalogHelper.loadText(is);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (caching) {
                cache.put(file, answer);
            }
        }

        return answer;
    }

    /**
     * Special logic for log endpoints to deal when showAll=true
     */
    private Map<String, String> filterProperties(String scheme, Map<String, String> options) {
        if ("log".equals(scheme)) {
            String showAll = options.get("showAll");
            if ("true".equals(showAll)) {
                Map<String, String> filtered = new LinkedHashMap<String, String>();
                // remove all the other showXXX options when showAll=true
                for (Map.Entry<String, String> entry : options.entrySet()) {
                    String key = entry.getKey();
                    boolean skip = key.startsWith("show") && !key.equals("showAll");
                    if (!skip) {
                        filtered.put(key, entry.getValue());
                    }
                }
                return filtered;
            }
        }
        // use as-is
        return options;
    }

    @Override
    public String listComponentsAsJson() {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("listComponentsAsJson");
        }

        if (answer == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<String> names = findComponentNames();
            for (int i = 0; i < names.size(); i++) {
                String scheme = names.get(i);
                String json = componentJSonSchema(scheme);
                // skip first line
                json = CatalogHelper.between(json, "\"component\": {", "\"componentProperties\": {");
                json = json != null ? json.trim() : "";
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
            answer = sb.toString();
            if (caching) {
                cache.put("listComponentsAsJson", answer);
            }
        }

        return answer;
    }

    @Override
    public String listDataFormatsAsJson() {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("listDataFormatsAsJson");
        }

        if (answer == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<String> names = findDataFormatNames();
            for (int i = 0; i < names.size(); i++) {
                String scheme = names.get(i);
                String json = dataFormatJSonSchema(scheme);
                // skip first line
                json = CatalogHelper.between(json, "\"dataformat\": {", "\"properties\": {");
                json = json != null ? json.trim() : "";
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
            answer = sb.toString();
            if (caching) {
                cache.put("listDataFormatsAsJson", answer);
            }
        }

        return answer;
    }

    @Override
    public String listLanguagesAsJson() {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("listLanguagesAsJson");
        }

        if (answer == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<String> names = findLanguageNames();
            for (int i = 0; i < names.size(); i++) {
                String scheme = names.get(i);
                String json = languageJSonSchema(scheme);
                // skip first line
                json = CatalogHelper.between(json, "\"language\": {", "\"properties\": {");
                json = json != null ? json.trim() : "";
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
            answer = sb.toString();
            if (caching) {
                cache.put("listLanguagesAsJson", answer);
            }
        }

        return answer;
    }

    @Override
    public String listModelsAsJson() {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("listModelsAsJson");
        }

        if (answer == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<String> names = findModelNames();
            for (int i = 0; i < names.size(); i++) {
                String scheme = names.get(i);
                String json = modelJSonSchema(scheme);
                // skip first line
                json = CatalogHelper.between(json, "\"model\": {", "\"properties\": {");
                json = json != null ? json.trim() : "";
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
            answer = sb.toString();
            if (caching) {
                cache.put("listModelsAsJson", answer);
            }
        }

        return answer;
    }

    @Override
    public String listOthersAsJson() {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("listOthersAsJson");
        }

        if (answer == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<String> names = findOtherNames();
            for (int i = 0; i < names.size(); i++) {
                String scheme = names.get(i);
                String json = otherJSonSchema(scheme);
                // skip first line
                json = CatalogHelper.between(json, "\"other\": {", "  }");
                json = json != null ? json.trim() : "";
                json = json + "\n  },";
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
            answer = sb.toString();
            if (caching) {
                cache.put("listOthersAsJson", answer);
            }
        }

        return answer;
    }

    @Override
    public String summaryAsJson() {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("summaryAsJson");
        }

        if (answer == null) {
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
            sb.append("  \"version\": \"").append(getCatalogVersion()).append("\",\n");
            sb.append("  \"eips\": ").append(findModelNames().size()).append(",\n");
            sb.append("  \"components\": ").append(findComponentNames().size()).append(",\n");
            sb.append("  \"dataformats\": ").append(findDataFormatNames().size()).append(",\n");
            sb.append("  \"languages\": ").append(findLanguageNames().size()).append(",\n");
            sb.append("  \"archetypes\": ").append(archetypes).append("\n");
            sb.append("}");
            answer = sb.toString();
            if (caching) {
                cache.put("summaryAsJson", answer);
            }
        }

        return answer;
    }

 // CHECKSTYLE:ON

}
