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
import java.util.ArrayList;
import java.util.List;

public class DefaultRuntimeProvider implements RuntimeProvider {

    private static final String COMPONENT_DIR = "org/apache/camel/catalog/components";
    private static final String DATAFORMAT_DIR = "org/apache/camel/catalog/dataformats";
    private static final String LANGUAGE_DIR = "org/apache/camel/catalog/languages";
    private static final String OTHER_DIR = "org/apache/camel/catalog/others";
    private static final String COMPONENTS_CATALOG = "org/apache/camel/catalog/components.properties";
    private static final String DATA_FORMATS_CATALOG = "org/apache/camel/catalog/dataformats.properties";
    private static final String LANGUAGE_CATALOG = "org/apache/camel/catalog/languages.properties";
    private static final String OTHER_CATALOG = "org/apache/camel/catalog/others.properties";

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
    public String getOtherJSonSchemaDirectory() {
        return OTHER_DIR;
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

    protected String getOtherCatalog() {
        return OTHER_CATALOG;
    }

    @Override
    public List<String> findComponentNames() {
        List<String> names = new ArrayList<String>();
        InputStream is = getCamelCatalog().getVersionManager().getResourceAsStream(getComponentsCatalog());
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
        InputStream is = getCamelCatalog().getVersionManager().getResourceAsStream(getDataFormatsCatalog());
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
        InputStream is = getCamelCatalog().getVersionManager().getResourceAsStream(getLanguageCatalog());
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
    public List<String> findOtherNames() {
        List<String> names = new ArrayList<String>();
        InputStream is = getCamelCatalog().getVersionManager().getResourceAsStream(getOtherCatalog());
        if (is != null) {
            try {
                CatalogHelper.loadLines(is, names);
            } catch (IOException e) {
                // ignore
            }
        }
        return names;
    }
}
