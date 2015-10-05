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
package org.apache.camel.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.CatalogHelper;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.commands.internal.RegexUtil;
import org.apache.camel.util.JsonSchemaHelper;

/**
 * Abstract {@link org.apache.camel.commands.CamelController} that implementators should extend.
 */
public abstract class AbstractCamelController implements CamelController {

    private CamelCatalog catalog = new DefaultCamelCatalog();

    @Override
    public List<Map<String, String>> getCamelContexts(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        List<Map<String, String>> context = getCamelContexts();
        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        } else {
            filter = "*";
        }
        for (Map<String, String> entry : context) {
            String name = entry.get("name");
            if (name.equalsIgnoreCase(filter) || CatalogHelper.matchWildcard(name, filter) || name.matches(filter)) {
                answer.add(entry);
            }
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> listEipsCatalog(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        }

        List<String> names = filter != null ? catalog.findModelNames(filter) : catalog.findModelNames();
        for (String name : names) {
            // load models json data, and parse it to gather the model meta-data
            String json = catalog.modelJSonSchema(name);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("model", json, false);

            String description = null;
            String label = null;
            String type = null;
            for (Map<String, String> row : rows) {
                if (row.containsKey("description")) {
                    description = row.get("description");
                } else if (row.containsKey("label")) {
                    label = row.get("label");
                } else if (row.containsKey("javaType")) {
                    type = row.get("javaType");
                }
            }

            Map<String, String> row = new HashMap<String, String>();
            row.put("name", name);
            if (description != null) {
                row.put("description", description);
            }
            if (label != null) {
                row.put("label", label);
            }
            if (type != null) {
                row.put("type", type);
            }

            answer.add(row);
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> listComponentsCatalog(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        }

        List<String> names = filter != null ? catalog.findComponentNames(filter) : catalog.findComponentNames();
        for (String name : names) {
            // load component json data, and parse it to gather the component meta-data
            String json = catalog.componentJSonSchema(name);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("component", json, false);

            String title = null;
            String description = null;
            String label = null;
            // the status can be:
            // - loaded = in use
            // - classpath = on the classpath
            // - release = available from the Apache Camel release
            String status = "release";
            String type = null;
            String groupId = null;
            String artifactId = null;
            String version = null;
            for (Map<String, String> row : rows) {
                if (row.containsKey("title")) {
                    title = row.get("title");
                } else if (row.containsKey("description")) {
                    description = row.get("description");
                } else if (row.containsKey("label")) {
                    label = row.get("label");
                } else if (row.containsKey("javaType")) {
                    type = row.get("javaType");
                } else if (row.containsKey("groupId")) {
                    groupId = row.get("groupId");
                } else if (row.containsKey("artifactId")) {
                    artifactId = row.get("artifactId");
                } else if (row.containsKey("version")) {
                    version = row.get("version");
                }
            }

            Map<String, String> row = new HashMap<String, String>();
            row.put("name", name);
            row.put("status", status);
            if (title != null) {
                row.put("title", title);
            }
            if (description != null) {
                row.put("description", description);
            }
            if (label != null) {
                row.put("label", label);
            }
            if (type != null) {
                row.put("type", type);
            }
            if (groupId != null) {
                row.put("groupId", groupId);
            }
            if (artifactId != null) {
                row.put("artifactId", artifactId);
            }
            if (version != null) {
                row.put("version", version);
            }

            answer.add(row);
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> listDataFormatsCatalog(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        }

        List<String> names = filter != null ? catalog.findDataFormatNames(filter) : catalog.findDataFormatNames();
        for (String name : names) {
            // load dataformat json data, and parse it to gather the dataformat meta-data
            String json = catalog.dataFormatJSonSchema(name);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("dataformat", json, false);

            String title = null;
            String description = null;
            String label = null;
            String modelName = name;
            // the status can be:
            // - loaded = in use
            // - classpath = on the classpath
            // - release = available from the Apache Camel release
            String status = "release";
            String type = null;
            String modelJavaType = null;
            String groupId = null;
            String artifactId = null;
            String version = null;
            for (Map<String, String> row : rows) {
                if (row.containsKey("modelName")) {
                    modelName = row.get("modelName");
                } else if (row.containsKey("title")) {
                    title = row.get("title");
                } else if (row.containsKey("description")) {
                    description = row.get("description");
                } else if (row.containsKey("label")) {
                    label = row.get("label");
                } else if (row.containsKey("javaType")) {
                    type = row.get("javaType");
                } else if (row.containsKey("modelJavaType")) {
                    modelJavaType = row.get("modelJavaType");
                } else if (row.containsKey("groupId")) {
                    groupId = row.get("groupId");
                } else if (row.containsKey("artifactId")) {
                    artifactId = row.get("artifactId");
                } else if (row.containsKey("version")) {
                    version = row.get("version");
                }
            }

            Map<String, String> row = new HashMap<String, String>();
            row.put("name", name);
            row.put("modelName", modelName);
            row.put("status", status);
            if (title != null) {
                row.put("title", title);
            }
            if (description != null) {
                row.put("description", description);
            }
            if (label != null) {
                row.put("label", label);
            }
            if (type != null) {
                row.put("type", type);
            }
            if (modelJavaType != null) {
                row.put("modelJavaType", modelJavaType);
            }
            if (groupId != null) {
                row.put("groupId", groupId);
            }
            if (artifactId != null) {
                row.put("artifactId", artifactId);
            }
            if (version != null) {
                row.put("version", version);
            }

            answer.add(row);
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> listLanguagesCatalog(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        }

        List<String> names = filter != null ? catalog.findLanguageNames(filter) : catalog.findLanguageNames();
        for (String name : names) {
            // load language json data, and parse it to gather the language meta-data
            String json = catalog.languageJSonSchema(name);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("language", json, false);

            String title = null;
            String description = null;
            String label = null;
            String modelName = name;
            // the status can be:
            // - loaded = in use
            // - classpath = on the classpath
            // - release = available from the Apache Camel release
            String status = "release";
            String type = null;
            String modelJavaType = null;
            String groupId = null;
            String artifactId = null;
            String version = null;
            for (Map<String, String> row : rows) {
                if (row.containsKey("modelName")) {
                    modelName = row.get("modelName");
                } else if (row.containsKey("title")) {
                    title = row.get("title");
                } else if (row.containsKey("description")) {
                    description = row.get("description");
                } else if (row.containsKey("label")) {
                    label = row.get("label");
                } else if (row.containsKey("javaType")) {
                    type = row.get("javaType");
                } else if (row.containsKey("modelJavaType")) {
                    modelJavaType = row.get("modelJavaType");
                } else if (row.containsKey("groupId")) {
                    groupId = row.get("groupId");
                } else if (row.containsKey("artifactId")) {
                    artifactId = row.get("artifactId");
                } else if (row.containsKey("version")) {
                    version = row.get("version");
                }
            }

            Map<String, String> row = new HashMap<String, String>();
            row.put("name", name);
            row.put("modelName", modelName);
            row.put("status", status);
            if (title != null) {
                row.put("title", title);
            }
            if (description != null) {
                row.put("description", description);
            }
            if (label != null) {
                row.put("label", label);
            }
            if (type != null) {
                row.put("type", type);
            }
            if (modelJavaType != null) {
                row.put("modelJavaType", modelJavaType);
            }
            if (groupId != null) {
                row.put("groupId", groupId);
            }
            if (artifactId != null) {
                row.put("artifactId", artifactId);
            }
            if (version != null) {
                row.put("version", version);
            }

            answer.add(row);
        }

        return answer;
    }

    @Override
    public Map<String, Set<String>> listEipsLabelCatalog() throws Exception {
        Map<String, Set<String>> answer = new LinkedHashMap<String, Set<String>>();

        Set<String> labels = catalog.findModelLabels();
        for (String label : labels) {
            List<Map<String, String>> models = listEipsCatalog(label);
            if (!models.isEmpty()) {
                Set<String> names = new LinkedHashSet<String>();
                for (Map<String, String> info : models) {
                    String name = info.get("name");
                    if (name != null) {
                        names.add(name);
                    }
                }
                answer.put(label, names);
            }
        }

        return answer;
    }

    @Override
    public Map<String, Set<String>> listComponentsLabelCatalog() throws Exception {
        Map<String, Set<String>> answer = new LinkedHashMap<String, Set<String>>();

        Set<String> labels = catalog.findComponentLabels();
        for (String label : labels) {
            List<Map<String, String>> components = listComponentsCatalog(label);
            if (!components.isEmpty()) {
                Set<String> names = new LinkedHashSet<String>();
                for (Map<String, String> info : components) {
                    String name = info.get("name");
                    if (name != null) {
                        names.add(name);
                    }
                }
                answer.put(label, names);
            }
        }

        return answer;
    }

    @Override
    public Map<String, Set<String>> listDataFormatsLabelCatalog() throws Exception {
        Map<String, Set<String>> answer = new LinkedHashMap<String, Set<String>>();

        Set<String> labels = catalog.findDataFormatLabels();
        for (String label : labels) {
            List<Map<String, String>> dataFormats = listDataFormatsCatalog(label);
            if (!dataFormats.isEmpty()) {
                Set<String> names = new LinkedHashSet<String>();
                for (Map<String, String> info : dataFormats) {
                    String name = info.get("name");
                    if (name != null) {
                        names.add(name);
                    }
                }
                answer.put(label, names);
            }
        }

        return answer;
    }

    @Override
    public Map<String, Set<String>> listLanguagesLabelCatalog() throws Exception {
        Map<String, Set<String>> answer = new LinkedHashMap<String, Set<String>>();

        Set<String> labels = catalog.findLanguageLabels();
        for (String label : labels) {
            List<Map<String, String>> languages = listLanguagesCatalog(label);
            if (!languages.isEmpty()) {
                Set<String> names = new LinkedHashSet<String>();
                for (Map<String, String> info : languages) {
                    String name = info.get("name");
                    if (name != null) {
                        names.add(name);
                    }
                }
                answer.put(label, names);
            }
        }

        return answer;
    }

}
