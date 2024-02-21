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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.camel.catalog.impl.CatalogHelper;

/**
 * {@link JSonSchemaResolver} used by {@link CamelCatalog} that is able to load all the resources that the complete
 * camel-catalog JAR provides.
 */
public class CamelCatalogJSonSchemaResolver implements JSonSchemaResolver {

    private static final String MODEL_DIR = "org/apache/camel/catalog/models";
    public static final String EXTENSION = ".json";

    private final CamelCatalog camelCatalog;
    private ClassLoader classLoader;

    // 3rd party components/data-formats
    private final Map<String, String> extraComponents;
    private final Map<String, String> extraComponentsJSonSchema;
    private final Map<String, String> extraDataFormats;
    private final Map<String, String> extraDataFormatsJSonSchema;

    public CamelCatalogJSonSchemaResolver(CamelCatalog camelCatalog,
                                          Map<String, String> extraComponents, Map<String, String> extraComponentsJSonSchema,
                                          Map<String, String> extraDataFormats,
                                          Map<String, String> extraDataFormatsJSonSchema) {
        this.camelCatalog = camelCatalog;
        this.extraComponents = extraComponents;
        this.extraComponentsJSonSchema = extraComponentsJSonSchema;
        this.extraDataFormats = extraDataFormats;
        this.extraDataFormatsJSonSchema = extraDataFormatsJSonSchema;
    }

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public String getComponentJSonSchema(String name) {
        final String file = camelCatalog.getRuntimeProvider().getComponentJSonSchemaDirectory() + "/" + name + EXTENSION;

        final String fromVersionManager = loadResourceFromVersionManager(file);
        if (fromVersionManager != null) {
            return fromVersionManager;
        }

        // its maybe a third party so try to see if we have the json schema already
        final String answer = extraComponentsJSonSchema.get(name);
        if (answer != null) {
            return answer;
        }

        // or if we can load it from the classpath
        final String className = extraComponents.get(name);
        return loadFromClasspath(className, name);
    }

    @Override
    public String getDataFormatJSonSchema(String name) {
        final String file = camelCatalog.getRuntimeProvider().getDataFormatJSonSchemaDirectory() + "/" + name + EXTENSION;

        final String fromVersionManager = loadResourceFromVersionManager(file);
        if (fromVersionManager != null) {
            return fromVersionManager;
        }

        // its maybe a third party so try to see if we have the json schema already
        final String schema = extraDataFormatsJSonSchema.get(name);
        if (schema != null) {
            return schema;
        }

        // or if we can load it from the classpath
        final String className = extraDataFormats.get(name);
        return loadFromClasspath(className, name);
    }

    @Override
    public String getLanguageJSonSchema(String name) {
        // if we try to look method then its in the bean.json file
        if ("method".equals(name)) {
            name = "bean";
        }
        final String file = camelCatalog.getRuntimeProvider().getLanguageJSonSchemaDirectory() + "/" + name + EXTENSION;
        return loadResourceFromVersionManager(file);
    }

    @Override
    public String getTransformerJSonSchema(String name) {
        name = sanitizeFileName(name);
        final String file = camelCatalog.getRuntimeProvider().getTransformerJSonSchemaDirectory() + "/" + name + EXTENSION;
        return loadResourceFromVersionManager(file);
    }

    @Override
    public String getModelJSonSchema(String name) {
        final String file = MODEL_DIR + "/" + name + EXTENSION;
        return loadResourceFromVersionManager(file);
    }

    @Override
    public String getMainJsonSchema() {
        final String file = "org/apache/camel/catalog/main/camel-main-configuration-metadata.json";
        return loadResourceFromVersionManager(file);
    }

    @Override
    public String getOtherJSonSchema(String name) {
        final String file = camelCatalog.getRuntimeProvider().getOtherJSonSchemaDirectory() + "/" + name + EXTENSION;
        return loadResourceFromVersionManager(file);
    }

    String loadFromClasspath(final String className, final String fileName) {
        if (className != null) {
            String packageName = className.substring(0, className.lastIndexOf('.'));
            packageName = packageName.replace('.', '/');
            final String path = packageName + "/" + fileName + EXTENSION;
            return loadResourceFromVersionManager(path);
        }

        return null;
    }

    String loadResourceFromVersionManager(final String file) {
        try (final InputStream is = camelCatalog.getVersionManager().getResourceAsStream(file)) {
            if (is != null) {
                return CatalogHelper.loadText(is);
            }
        } catch (IOException e) {
            // ignore
        }
        if (classLoader != null) {
            try (InputStream is = classLoader.getResourceAsStream(file)) {
                if (is != null) {
                    return CatalogHelper.loadText(is);
                }
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9-]", "-");
    }
}
