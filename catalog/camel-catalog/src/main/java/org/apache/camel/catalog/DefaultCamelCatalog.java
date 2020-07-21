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
package org.apache.camel.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import org.apache.camel.catalog.impl.AbstractCamelCatalog;
import org.apache.camel.catalog.impl.CatalogHelper;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.util.json.JsonObject;

/**
 * Default {@link CamelCatalog}.
 */
public class DefaultCamelCatalog extends AbstractCamelCatalog implements CamelCatalog {

    private static final String MODELS_CATALOG = "org/apache/camel/catalog/models.properties";
    private static final String MODEL_DIR = "org/apache/camel/catalog/models";
    private static final String DOC_DIR = "org/apache/camel/catalog/docs";
    private static final String ARCHETYPES_CATALOG = "org/apache/camel/catalog/archetypes/archetype-catalog.xml";
    private static final String SCHEMAS_XML = "org/apache/camel/catalog/schemas";
    private static final String MAIN_DIR = "org/apache/camel/catalog/main";

    private final VersionHelper version = new VersionHelper();

    // 3rd party components/data-formats
    private final Map<String, String> extraComponents = new HashMap<>();
    private final Map<String, String> extraComponentsJSonSchema = new HashMap<>();
    private final Map<String, String> extraDataFormats = new HashMap<>();
    private final Map<String, String> extraDataFormatsJSonSchema = new HashMap<>();

    // cache of operation -> result
    private final Map<String, Object> cache = new HashMap<>();

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
    public List<String> findComponentNames() {
        return cache("findComponentNames", () ->
                Stream.of(runtimeProvider.findComponentNames(), extraComponents.keySet())
                        .flatMap(Collection::stream)
                        .sorted()
                        .collect(Collectors.toList()));
    }

    @Override
    public List<String> findDataFormatNames() {
        return cache("findDataFormatNames", () ->
            Stream.of(runtimeProvider.findDataFormatNames(), extraDataFormats.keySet())
                    .flatMap(Collection::stream)
                    .sorted()
                    .collect(Collectors.toList()));
    }

    @Override
    public List<String> findLanguageNames() {
        return cache("findLanguageNames", runtimeProvider::findLanguageNames);
    }

    @Override
    public List<String> findModelNames() {
        return cache("findModelNames", () -> {
            try (InputStream is = versionManager.getResourceAsStream(MODELS_CATALOG)) {
                return CatalogHelper.loadLines(is);
            } catch (IOException e) {
                return Collections.emptyList();
            }
        });
    }

    @Override
    public List<String> findOtherNames() {
        return cache("findOtherNames", runtimeProvider::findOtherNames);
    }

    @Override
    public List<String> findModelNames(String filter) {
        // should not cache when filter parameter can by any kind of value
        return findNames(filter, this::findModelNames, this::eipModel);
    }

    @Override
    public List<String> findComponentNames(String filter) {
        // should not cache when filter parameter can by any kind of value
        return findNames(filter, this::findComponentNames, this::componentModel);
    }

    @Override
    public List<String> findDataFormatNames(String filter) {
        // should not cache when filter parameter can by any kind of value
        return findNames(filter, this::findDataFormatNames, this::dataFormatModel);
    }

    @Override
    public List<String> findLanguageNames(String filter) {
        // should not cache when filter parameter can by any kind of value
        return findNames(filter, this::findLanguageNames, this::languageModel);
    }

    @Override
    public List<String> findOtherNames(String filter) {
        // should not cache when filter parameter can by any kind of value
        return findNames(filter, this::findOtherNames, this::otherModel);
    }

    private List<String> findNames(String filter, Supplier<List<String>> findNames, Function<String, ? extends BaseModel<?>> modelLoader) {
        List<String> answer = new ArrayList<>();
        List<String> names = findNames.get();
        for (String name : names) {
            BaseModel<?> model = modelLoader.apply(name);
            if (model != null) {
                String label = model.getLabel();
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
        return answer;
    }

    @Override
    public String modelJSonSchema(String name) {
        return cache("eip-" + name, name, super::modelJSonSchema);
    }

    @Override
    public EipModel eipModel(String name) {
        return cache("eip-model-" + name, name, super::eipModel);
    }

    @Override
    public String componentJSonSchema(String name) {
        return cache("component-" + name, name, super::componentJSonSchema);
    }

    @Override
    public ComponentModel componentModel(String name) {
        return cache("component-model-" + name, name, super::componentModel);
    }

    @Override
    public String dataFormatJSonSchema(String name) {
        return cache("dataformat-" + name, name, super::dataFormatJSonSchema);
    }

    @Override
    public DataFormatModel dataFormatModel(String name) {
        return cache("dataformat-model-" + name, name, super::dataFormatModel);
    }

    @Override
    public String languageJSonSchema(String name) {
        return cache("language-" + name, name, super::languageJSonSchema);
    }

    @Override
    public LanguageModel languageModel(String name) {
        return cache("language-model-" + name, name, super::languageModel);
    }

    @Override
    public String otherJSonSchema(String name) {
        return cache("other-" + name, name, super::otherJSonSchema);
    }

    @Override
    public OtherModel otherModel(String name) {
        return cache("other-model-" + name, name, super::otherModel);
    }

    public String mainJSonSchema() {
        return cache("main", "main", k -> super.mainJSonSchema());
    }

    public MainModel mainModel() {
        return cache("main-model", "main-model", k -> super.mainModel());
    }

    @Override
    public String componentAsciiDoc(String name) {
        String answer = doComponentAsciiDoc(name);
        if (answer == null) {
            // maybe the name is an alternative scheme name, and then we need to find the component that
            // has the name as alternative, and use the first scheme as the name to find the documentation
            List<String> names = findComponentNames();
            for (String alternative : names) {
                String schemes = getAlternativeComponentName(alternative, name);
                if (schemes != null && schemes.contains(name)) {
                    String first = schemes.split(",")[0];
                    if (Objects.equals(first, name)) {
                        continue;
                    }
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
                String schemes = getAlternativeComponentName(alternative, name);
                if (schemes != null && schemes.contains(name)) {
                    String first = schemes.split(",")[0];
                    return componentHtmlDoc(first);
                }
            }
        }
        return answer;
    }

    private String getAlternativeComponentName(String componentName, String alternativeTo) {
        // optimize for this very call to avoid loading all schemas
        String json = componentJSonSchema(componentName);
        if (json.contains("alternativeSchemes") && json.contains(alternativeTo)) {
            ComponentModel model = componentModel(componentName);
            if (model != null) {
                return model.getAlternativeSchemes();
            }
        }
        return null;
    }

    private String doComponentAsciiDoc(String componentName) {
        // special for mail component
        String name;
        if (componentName.equals("imap") || componentName.equals("imaps") || componentName.equals("pop3") || componentName.equals("pop3s") || componentName.equals("smtp") || componentName.equals("smtps")) {
            name = "mail";
        } else {
            name = componentName;
        }
        String file = DOC_DIR + "/" + name + "-component.adoc";
        return cache(file, () -> {
            if (findComponentNames().contains(componentName)) {
                return loadResource(file);
            } else if (extraComponents.containsKey(name)) {
                String className = extraComponents.get(name);
                String packageName = className.substring(0, className.lastIndexOf('.'));
                packageName = packageName.replace('.', '/');
                String path = packageName + "/" + name + "-component.adoc";
                return loadResource(path);
            } else {
                return null;
            }
        });
    }

    private String doComponentHtmlDoc(String componentName) {
        // special for mail component
        String name;
        if (componentName.equals("imap") || componentName.equals("imaps") || componentName.equals("pop3") || componentName.equals("pop3s") || componentName.equals("smtp") || componentName.equals("smtps")) {
            name = "mail";
        } else {
            name = componentName;
        }
        String file = DOC_DIR + "/" + name + "-component.html";
        return cache(file, () -> {
            if (findComponentNames().contains(name)) {
                return loadResource(file);
            } else if (extraComponents.containsKey(name)) {
                String className = extraComponents.get(name);
                String packageName = className.substring(0, className.lastIndexOf('.'));
                packageName = packageName.replace('.', '/');
                String path = packageName + "/" + name + "-component.html";
                return loadResource(path);
            } else {
                return null;
            }
        });
    }

    @Override
    public String dataFormatAsciiDoc(String dataformatName) {
        // special for some name data formats
        String name;
        if (dataformatName.startsWith("bindy")) {
            name = "bindy";
        } else if (dataformatName.startsWith("univocity")) {
            name = "univocity";
        } else {
            name = dataformatName;
        }
        String file = DOC_DIR + "/" + name + "-dataformat.adoc";
        return cache(file, () -> {
            if (findDataFormatNames().contains(dataformatName)) {
                return loadResource(file);
            } else if (extraDataFormats.containsKey(name)) {
                String className = extraDataFormats.get(name);
                String packageName = className.substring(0, className.lastIndexOf('.'));
                packageName = packageName.replace('.', '/');
                String path = packageName + "/" + name + "-dataformat.adoc";
                return loadResource(path);
            } else {
                return null;
            }
        });
    }

    @Override
    public String dataFormatHtmlDoc(String dataformatName) {
        // special for some name data formats
        String name;
        if (dataformatName.startsWith("bindy")) {
            name = "bindy";
        } else if (dataformatName.startsWith("univocity")) {
            name = "univocity";
        } else {
            name = dataformatName;
        }
        String file = DOC_DIR + "/" + name + "-dataformat.html";
        return cache(file, () -> {
            if (findDataFormatNames().contains(name)) {
                return loadResource(file);
            } else if (extraDataFormats.containsKey(name)) {
                String className = extraDataFormats.get(name);
                String packageName = className.substring(0, className.lastIndexOf('.'));
                packageName = packageName.replace('.', '/');
                String path = packageName + "/" + name + "-dataformat.html";
                return loadResource(path);
            } else {
                return null;
            }
        });
    }

    @Override
    public String languageAsciiDoc(String name) {
        // if we try to look method then its in the bean.adoc file
        if ("method".equals(name)) {
            name = "bean";
        }
        String file = DOC_DIR + "/" + name + "-language.adoc";
        return cache(file, this::loadResource);
    }

    @Override
    public String languageHtmlDoc(String name) {
        // if we try to look method then its in the bean.html file
        if ("method".equals(name)) {
            name = "bean";
        }
        String file = DOC_DIR + "/" + name + "-language.html";
        return cache(file, this::loadResource);
    }

    @Override
    public String otherAsciiDoc(String name) {
        String file = DOC_DIR + "/" + name + ".adoc";
        return cache(file, this::loadResource);
    }

    @Override
    public String otherHtmlDoc(String name) {
        String file = DOC_DIR + "/" + name + "-other.html";
        return cache(file, this::loadResource);
    }

    @Override
    public Set<String> findModelLabels() {
        return cache("findModelLabels", () -> findLabels(this::findModelNames, this::eipModel));
    }

    @Override
    public Set<String> findComponentLabels() {
        return cache("findComponentLabels", () -> findLabels(this::findComponentNames, this::componentModel));
    }

    @Override
    public Set<String> findDataFormatLabels() {
        return cache("findDataFormatLabels", () -> findLabels(this::findDataFormatNames, this::dataFormatModel));
    }

    @Override
    public Set<String> findLanguageLabels() {
        return cache("findLanguageLabels", () -> findLabels(this::findLanguageNames, this::languageModel));
    }

    @Override
    public Set<String> findOtherLabels() {
        return cache("findOtherLabels", () -> findLabels(this::findOtherNames, this::otherModel));
    }

    private SortedSet<String> findLabels(Supplier<List<String>> findNames, Function<String, ? extends BaseModel<?>> loadModel) {
        TreeSet<String> answer = new TreeSet<>();
        List<String> names = findNames.get();
        for (String name : names) {
            BaseModel<?> model = loadModel.apply(name);
            if (model != null) {
                String label = model.getLabel();
                String[] parts = label.split(",");
                Collections.addAll(answer, parts);
            }
        }
        return answer;
    }

    @Override
    public String archetypeCatalogAsXml() {
        return cache(ARCHETYPES_CATALOG, this::loadResource);
    }

    @Override
    public String springSchemaAsXml() {
        return cache(SCHEMAS_XML + "/camel-spring.xsd", this::loadResource);
    }

    @Override
    public String mainJsonSchema() {
        return cache(MAIN_DIR + "/camel-main-configuration-metadata.json", this::loadResource);
    }

    @Override
    public String listComponentsAsJson() {
        return cache("listComponentsAsJson", () ->
                JsonMapper.serialize(findComponentNames().stream()
                    .map(this::componentJSonSchema)
                    .map(JsonMapper::deserialize)
                    .map(o -> o.get("component"))
                    .collect(Collectors.toList())));
    }

    @Override
    public String listDataFormatsAsJson() {
        return cache("listDataFormatsAsJson", () ->
                JsonMapper.serialize(findDataFormatNames().stream()
                        .map(this::dataFormatJSonSchema)
                        .map(JsonMapper::deserialize)
                        .map(o -> o.get("dataformat"))
                        .collect(Collectors.toList())));
    }

    @Override
    public String listLanguagesAsJson() {
        return cache("listLanguagesAsJson", () ->
                JsonMapper.serialize(findLanguageNames().stream()
                        .map(this::languageJSonSchema)
                        .map(JsonMapper::deserialize)
                        .map(o -> o.get("language"))
                        .collect(Collectors.toList())));
    }

    @Override
    public String listModelsAsJson() {
        return cache("listModelsAsJson", () ->
                JsonMapper.serialize(findModelNames().stream()
                        .map(this::modelJSonSchema)
                        .map(JsonMapper::deserialize)
                        .map(o -> o.get("model"))
                        .collect(Collectors.toList())));
    }

    @Override
    public String listOthersAsJson() {
        return cache("listOthersAsJson", () ->
                JsonMapper.serialize(findOtherNames().stream()
                        .map(this::otherJSonSchema)
                        .map(JsonMapper::deserialize)
                        .map(o -> o.get("other"))
                        .collect(Collectors.toList())));
    }

    @Override
    public String summaryAsJson() {
        return cache("summaryAsJson", () -> {
            Map<String, Object> obj = new JsonObject();
            obj.put("version", getCatalogVersion());
            obj.put("eips", findModelNames().size());
            obj.put("components", findComponentNames().size());
            obj.put("dataformats", findDataFormatNames().size());
            obj.put("languages", findLanguageNames().size());
            obj.put("archetypes", getArchetypesCount());
            return JsonMapper.serialize(obj);
        });
    }

    private int getArchetypesCount() {
        int archetypes = 0;
        try {
            String xml = archetypeCatalogAsXml();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE);
            Document dom = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));
            Object val = XPathFactory.newInstance().newXPath().evaluate("count(/archetype-catalog/archetypes/archetype)", dom, XPathConstants.NUMBER);
            double num = (double) val;
            archetypes = (int) num;
        } catch (Exception e) {
            // ignore
        }
        return archetypes;
    }

    @SuppressWarnings("unchecked")
    private <T> T cache(String name, Supplier<T> loader) {
        if (caching) {
            T t = (T) cache.get(name);
            if (t == null) {
                t = loader.get();
                if (t != null) {
                    cache.put(name, t);
                }
            }
            return t;
        } else {
            return loader.get();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cache(String key, String name, Function<String, T> loader) {
        if (caching) {
            T t = (T) cache.get(key);
            if (t == null) {
                t = loader.apply(name);
                if (t != null) {
                    cache.put(key, t);
                }
            }
            return t;
        } else {
            return loader.apply(name);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cache(String name, Function<String, T> loader) {
        if (caching) {
            T t = (T) cache.get(name);
            if (t == null) {
                t = loader.apply(name);
                if (t != null) {
                    cache.put(name, t);
                }
            }
            return t;
        } else {
            return loader.apply(name);
        }
    }

    private String loadResource(String file) {
        try (InputStream is = versionManager.getResourceAsStream(file)) {
            return is != null ? CatalogHelper.loadText(is) : null;
        } catch (IOException e) {
            return null;
        }
    }

    // CHECKSTYLE:ON

}
