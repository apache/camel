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
package org.apache.camel.dsl.jbang.core.model;

import java.util.Map;

import org.apache.camel.util.json.JsonObject;

public class CatalogBaseDTO {

    private String name;
    private String title;
    private String level;
    private String since;
    private boolean nativeSupported;
    private String description;
    private String label;
    private String gav;
    private boolean deprecated;

    public CatalogBaseDTO() {
    }

    public CatalogBaseDTO(String name, String title, String level, String since, boolean nativeSupported, String description,
                          String label, String gav, boolean deprecated) {
        this.name = name;
        this.title = title;
        this.level = level;
        this.since = since;
        this.nativeSupported = nativeSupported;
        this.description = description;
        this.label = label;
        this.gav = gav;
        this.deprecated = deprecated;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getSince() {
        return since;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public boolean isNativeSupported() {
        return nativeSupported;
    }

    public void setNativeSupported(boolean nativeSupported) {
        this.nativeSupported = nativeSupported;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getGav() {
        return gav;
    }

    public void setGav(String gav) {
        this.gav = gav;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public Map<String, Object> toMap() {
        JsonObject jo = new JsonObject();
        jo.put("name", name);
        jo.put("title", title);
        jo.put("level", level);
        jo.put("since", since);
        jo.put("nativeSupported", nativeSupported);
        if (description != null) {
            jo.put("description", description);
        }
        if (label != null) {
            jo.put("label", label);
        }
        jo.put("gav", gav);
        jo.put("deprecated", deprecated);
        return jo;
    }
}
