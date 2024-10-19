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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.impl.CatalogHelper;

public class DefaultRuntimeProvider implements RuntimeProvider {

    private static final String COMPONENT_DIR = "org/apache/camel/catalog/components";
    private static final String DATAFORMAT_DIR = "org/apache/camel/catalog/dataformats";
    private static final String LANGUAGE_DIR = "org/apache/camel/catalog/languages";
    private static final String TRANSFORMER_DIR = "org/apache/camel/catalog/transformers";
    private static final String CONSOLE_DIR = "org/apache/camel/catalog/dev-consoles";
    private static final String OTHER_DIR = "org/apache/camel/catalog/others";
    private static final String BEANS_DIR = "org/apache/camel/catalog/beans";
    private static final String COMPONENTS_CATALOG = "org/apache/camel/catalog/components.properties";
    private static final String DATA_FORMATS_CATALOG = "org/apache/camel/catalog/dataformats.properties";
    private static final String LANGUAGE_CATALOG = "org/apache/camel/catalog/languages.properties";
    private static final String TRANSFORMER_CATALOG = "org/apache/camel/catalog/transformers.properties";
    private static final String CONSOLE_CATALOG = "org/apache/camel/catalog/dev-consoles.properties";
    private static final String OTHER_CATALOG = "org/apache/camel/catalog/others.properties";
    private static final String BEANS_CATALOG = "org/apache/camel/catalog/beans.properties";

    private CamelCatalog camelCatalog;

    public DefaultRuntimeProvider() {
    }

    public DefaultRuntimeProvider(CamelCatalog camelCatalog) {
        this.camelCatalog = camelCatalog;
    }

    @Override
    public CamelCatalog getCamelCatalog() {
        return camelCatalog;
    }

    @Override
    public void setCamelCatalog(CamelCatalog camelCatalog) {
        this.camelCatalog = camelCatalog;
    }

    @Override
    public String getProviderName() {
        return "default";
    }

    @Override
    public String getProviderGroupId() {
        return "org.apache.camel";
    }

    @Override
    public String getProviderArtifactId() {
        return "camel-catalog";
    }

    @Override
    public String getComponentJSonSchemaDirectory() {
        return COMPONENT_DIR;
    }

    @Override
    public String getDataFormatJSonSchemaDirectory() {
        return DATAFORMAT_DIR;
    }

    @Override
    public String getLanguageJSonSchemaDirectory() {
        return LANGUAGE_DIR;
    }

    @Override
    public String getTransformerJSonSchemaDirectory() {
        return TRANSFORMER_DIR;
    }

    @Override
    public String getDevConsoleJSonSchemaDirectory() {
        return CONSOLE_DIR;
    }

    @Override
    public String getOtherJSonSchemaDirectory() {
        return OTHER_DIR;
    }

    @Override
    public String getPojoBeanJSonSchemaDirectory() {
        return BEANS_DIR;
    }

    protected String getComponentsCatalog() {
        return COMPONENTS_CATALOG;
    }

    protected String getDataFormatsCatalog() {
        return DATA_FORMATS_CATALOG;
    }

    protected String getLanguageCatalog() {
        return LANGUAGE_CATALOG;
    }

    protected String getTransformerCatalog() {
        return TRANSFORMER_CATALOG;
    }

    protected String getDevConsoleCatalog() {
        return CONSOLE_CATALOG;
    }

    protected String getOtherCatalog() {
        return OTHER_CATALOG;
    }

    protected String getBeansCatalog() {
        return BEANS_CATALOG;
    }

    @Override
    public List<String> findComponentNames() {
        return find(getComponentsCatalog());
    }

    @Override
    public List<String> findDataFormatNames() {
        return find(getDataFormatsCatalog());
    }

    @Override
    public List<String> findLanguageNames() {
        return find(getLanguageCatalog());
    }

    @Override
    public List<String> findTransformerNames() {
        return find(getTransformerCatalog());
    }

    @Override
    public List<String> findDevConsoleNames() {
        return find(getDevConsoleCatalog());
    }

    @Override
    public List<String> findOtherNames() {
        return find(getOtherCatalog());
    }

    @Override
    public List<String> findBeansNames() {
        return find(getBeansCatalog());
    }

    protected List<String> find(String resourceName) {
        List<String> names = new ArrayList<>();
        try (InputStream is = getCamelCatalog().getVersionManager().getResourceAsStream(resourceName)) {
            if (is != null) {
                try {
                    CatalogHelper.loadLines(is, names);
                } catch (IOException e) {
                    // ignore
                }
            }
        } catch (IOException e1) {
            // ignore
        }
        return names;
    }
}
