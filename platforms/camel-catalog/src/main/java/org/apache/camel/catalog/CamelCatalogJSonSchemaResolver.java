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
import java.util.HashMap;
import java.util.Map;

/**
 * {@link JSonSchemaResolver} used by {@link CamelCatalog} that is able to load all the resources that the complete camel-catalog JAR provides.
 */
public class CamelCatalogJSonSchemaResolver implements JSonSchemaResolver {

    private static final String MODEL_DIR = "org/apache/camel/catalog/models";

    private final CamelCatalog camelCatalog;

    // 3rd party components/data-formats
    private final Map<String, String> extraComponents;
    private final Map<String, String> extraComponentsJSonSchema;
    private final Map<String, String> extraDataFormats;
    private final Map<String, String> extraDataFormatsJSonSchema;

    public CamelCatalogJSonSchemaResolver(CamelCatalog camelCatalog,
                                          Map<String, String> extraComponents, Map<String, String> extraComponentsJSonSchema,
                                          Map<String, String> extraDataFormats, Map<String, String> extraDataFormatsJSonSchema) {
        this.camelCatalog = camelCatalog;
        this.extraComponents = extraComponents;
        this.extraComponentsJSonSchema = extraComponentsJSonSchema;
        this.extraDataFormats = extraDataFormats;
        this.extraDataFormatsJSonSchema = extraDataFormatsJSonSchema;
    }

    @Override
    public String getComponentJSonSchema(String name) {
        String file = camelCatalog.getRuntimeProvider().getComponentJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(file);
        if (is != null) {
            try {
                answer = CatalogHelper.loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }
        if (answer == null) {
            // its maybe a third party so try to see if we have the json schema already
            answer = extraComponentsJSonSchema.get(name);
            if (answer == null) {
                // or if we can load it from the classpath
                String className = extraComponents.get(name);
                if (className != null) {
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    packageName = packageName.replace('.', '/');
                    String path = packageName + "/" + name + ".json";
                    is = camelCatalog.getVersionManager().getResourceAsStream(path);
                    if (is != null) {
                        try {
                            answer = CatalogHelper.loadText(is);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public String getDataFormatJSonSchema(String name) {
        String file = camelCatalog.getRuntimeProvider().getDataFormatJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(file);
        if (is != null) {
            try {
                answer = CatalogHelper.loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }
        if (answer == null) {
            // its maybe a third party so try to see if we have the json schema already
            answer = extraDataFormatsJSonSchema.get(name);
            if (answer == null) {
                // or if we can load it from the classpath
                String className = extraDataFormats.get(name);
                if (className != null) {
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    packageName = packageName.replace('.', '/');
                    String path = packageName + "/" + name + ".json";
                    is = camelCatalog.getVersionManager().getResourceAsStream(path);
                    if (is != null) {
                        try {
                            answer = CatalogHelper.loadText(is);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public String getLanguageJSonSchema(String name) {
        // if we try to look method then its in the bean.json file
        if ("method".equals(name)) {
            name = "bean";
        }

        String file = camelCatalog.getRuntimeProvider().getLanguageJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(file);
        if (is != null) {
            try {
                answer = CatalogHelper.loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return answer;
    }

    @Override
    public String getOtherJSonSchema(String name) {
        String file = camelCatalog.getRuntimeProvider().getOtherJSonSchemaDirectory() + "/" + name + ".json";

        String answer = null;
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(file);
        if (is != null) {
            try {
                answer = CatalogHelper.loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return answer;
    }

    @Override
    public String getModelJSonSchema(String name) {
        String file = MODEL_DIR + "/" + name + ".json";

        String answer = null;

        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(file);
        if (is != null) {
            try {
                answer = CatalogHelper.loadText(is);
            } catch (IOException e) {
                // ignore
            }
        }

        return answer;
    }
}
