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
package org.apache.camel.catalog.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.util.json.JsonObject;

@DevConsole("catalog")
public class CatalogConsole extends AbstractDevConsole {

    private static final String CP = System.getProperty("java.class.path");
    private final CamelCatalog catalog = new DefaultCamelCatalog(true);

    public CatalogConsole() {
        super("camel", "catalog", "Catalog", "Lists all the used Camel Components");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        sb.append("\nComponents:\n");
        getCamelContext().getComponentNames().forEach(n -> appendModel(catalog.componentModel(n), sb));
        sb.append("\n\nLanguages:\n");
        getCamelContext().getLanguageNames().forEach(n -> appendModel(catalog.languageModel(n), sb));
        sb.append("\n\nData Formats:\n");
        getCamelContext().getDataFormatNames().forEach(n -> appendModel(catalog.dataFormatModel(n), sb));

        // misc is harder to find as we need to find them via classpath
        sb.append("\n\nMiscellaneous Components:\n");
        String[] cp = CP.split("[:|;]");
        String suffix = "-" + getCamelContext().getVersion() + ".jar";
        for (String c : cp) {
            if (c.endsWith(suffix)) {
                int pos = Math.max(c.lastIndexOf("/"), c.lastIndexOf("\\"));
                if (pos > 0) {
                    c = c.substring(pos + 1, c.length() - suffix.length());
                    appendModel(findOtherModel(c), sb);
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        List<JsonObject> components = new ArrayList<>();
        root.put("components", components);
        List<JsonObject> dataformat = new ArrayList<>();
        root.put("dataformat", dataformat);
        List<JsonObject> languages = new ArrayList<>();
        root.put("languages", languages);
        List<JsonObject> others = new ArrayList<>();
        root.put("others", others);

        getCamelContext().getComponentNames().forEach(n -> appendModel(catalog.componentModel(n), components));
        getCamelContext().getLanguageNames().forEach(n -> appendModel(catalog.languageModel(n), languages));
        getCamelContext().getDataFormatNames().forEach(n -> appendModel(catalog.dataFormatModel(n), dataformat));

        // misc is harder to find as we need to find them via classpath
        String[] cp = CP.split("[:|;]");
        String suffix = "-" + getCamelContext().getVersion() + ".jar";
        for (String c : cp) {
            if (c.endsWith(suffix)) {
                int pos = Math.max(c.lastIndexOf("/"), c.lastIndexOf("\\"));
                if (pos > 0) {
                    c = c.substring(pos + 1, c.length() - suffix.length());
                    appendModel(findOtherModel(c), others);
                }
            }
        }

        return root;
    }

    private ArtifactModel<?> findOtherModel(String artifactId) {
        // is it a mist component
        for (String name : catalog.findOtherNames()) {
            OtherModel model = catalog.otherModel(name);
            if (model != null && model.getArtifactId().equals(artifactId)) {
                return model;
            }
        }
        return null;
    }

    private static void appendModel(ArtifactModel<?> model, StringBuilder sb) {
        if (model != null) {
            String level = model.getSupportLevel().toString();
            if (model.isDeprecated()) {
                level += "-deprecated";
            }
            sb.append(String.format("\n    %s %s %s %s: %s", model.getArtifactId(), level,
                    model.getFirstVersionShort(), model.getTitle(), model.getDescription()));
        }
    }

    private static void appendModel(ArtifactModel<?> model, List<JsonObject> list) {
        if (model != null) {
            JsonObject jo = new JsonObject();
            String level = model.getSupportLevel().toString();
            if (model.isDeprecated()) {
                level += "-deprecated";
            }
            jo.put("groupId", model.getGroupId());
            jo.put("artifactId", model.getArtifactId());
            jo.put("version", model.getVersion());
            jo.put("level", level);
            jo.put("firstVersion", model.getFirstVersionShort());
            jo.put("title", model.getTitle());
            jo.put("description", model.getDescription());

            list.add(jo);
        }
    }
}
