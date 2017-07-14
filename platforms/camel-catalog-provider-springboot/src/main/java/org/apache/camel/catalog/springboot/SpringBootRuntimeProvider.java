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
package org.apache.camel.catalog.springboot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.CatalogHelper;
import org.apache.camel.catalog.RuntimeProvider;

/**
 * A Spring Boot based {@link RuntimeProvider} which only includes the supported Camel components, data formats, and languages
 * which can be installed in Spring Boot using the starter dependencies.
 */
public class SpringBootRuntimeProvider implements RuntimeProvider {

    private static final String COMPONENT_DIR = "org/apache/camel/catalog/springboot/components";
    private static final String DATAFORMAT_DIR = "org/apache/camel/catalog/springboot/dataformats";
    private static final String LANGUAGE_DIR = "org/apache/camel/catalog/springboot/languages";
    private static final String OTHER_DIR = "org/apache/camel/catalog/springboot/others";
    private static final String COMPONENTS_CATALOG = "org/apache/camel/catalog/springboot/components.properties";
    private static final String DATA_FORMATS_CATALOG = "org/apache/camel/catalog/springboot/dataformats.properties";
    private static final String LANGUAGE_CATALOG = "org/apache/camel/catalog/springboot/languages.properties";
    private static final String OTHER_CATALOG = "org/apache/camel/catalog/springboot/others.properties";

    private CamelCatalog camelCatalog;

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
        return "springboot";
    }

    @Override
    public String getProviderGroupId() {
        return "org.apache.camel";
    }

    @Override
    public String getProviderArtifactId() {
        return "camel-catalog-provider-springboot";
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

    @Override
    public List<String> findComponentNames() {
        List<String> names = new ArrayList<String>();
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(COMPONENTS_CATALOG);
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
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(DATA_FORMATS_CATALOG);
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
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(LANGUAGE_CATALOG);
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
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(OTHER_CATALOG);
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
