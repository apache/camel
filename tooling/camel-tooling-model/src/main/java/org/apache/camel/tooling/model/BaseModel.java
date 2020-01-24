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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public abstract class BaseModel<O extends BaseOptionModel> {

    protected String kind;
    protected String name;
    protected String title;
    protected String description;
    protected String firstVersion;
    protected String javaType;
    protected String label;
    protected boolean deprecated;
    protected String deprecationNote;
    protected final List<O> options = new ArrayList<>();

    public static Comparator<BaseModel<?>> compareTitle() {
        return (m1, m2) -> m1.getTitle().compareToIgnoreCase(m2.getTitle());
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFirstVersion() {
        return firstVersion;
    }

    public void setFirstVersion(String firstVersion) {
        this.firstVersion = firstVersion;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getDeprecationNote() {
        return deprecationNote;
    }

    public void setDeprecationNote(String deprecationNote) {
        this.deprecationNote = deprecationNote;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public List<O> getOptions() {
        return options;
    }

    public void addOption(O option) {
        options.add(option);
    }

    public String getShortJavaType() {
        return Strings.getClassShortName(javaType);
    }

    public String getFirstVersionShort() {
        return Strings.cutLastZeroDigit(firstVersion);
    }

    public static void parseModel(JsonObject mobj, BaseModel<?> model) {
        model.setTitle(mobj.getString("title"));
        model.setName(mobj.getString("name"));
        model.setDescription(mobj.getString("description"));
        model.setFirstVersion(mobj.getString("firstVersion"));
        model.setLabel(mobj.getString("label"));
        model.setDeprecated(mobj.getBooleanOrDefault("deprecated", false));
        model.setDeprecationNote(mobj.getString("label"));
        model.setJavaType(mobj.getString("javaType"));
    }

    public static void parseOption(JsonObject mp, BaseOptionModel option, String name) {
        option.setName(name);
        option.setKind(mp.getString("kind"));
        option.setDisplayName(mp.getString("displayName"));
        option.setGroup(mp.getString("group"));
        option.setLabel(mp.getString("label"));
        option.setRequired(mp.getBooleanOrDefault("required", false));
        option.setType(mp.getString("type"));
        option.setJavaType(mp.getString("javaType"));
        option.setEnums(asStringList(mp.getCollection("enum")));
        option.setOneOfs(asStringList(mp.getCollection("oneOf")));
        option.setPrefix(mp.getString("prefix"));
        option.setOptionalPrefix(mp.getString("optionalPrefix"));
        option.setMultiValue(mp.getBooleanOrDefault("multiValue", false));
        option.setDeprecated(mp.getBooleanOrDefault("deprecated", false));
        option.setDeprecationNote(mp.getString("deprecationNote"));
        option.setSecret(mp.getBooleanOrDefault("secret", false));
        option.setDefaultValue(mp.get("defaultValue"));
        option.setAsPredicate(mp.getBooleanOrDefault("asPredicate", false));
        option.setConfigurationClass(mp.getString("configurationClass"));
        option.setConfigurationField(mp.getString("configurationField"));
        option.setDescription(mp.getString("description"));
    }

    public static JsonObject asJsonObject(List<? extends BaseOptionModel> options) {
        JsonObject json = new JsonObject();
        options.forEach(option -> json.put(option.getName(), asJsonObject(option)));
        return json;
    }

    public static JsonObject asJsonObject(BaseOptionModel option) {
        JsonObject prop = new JsonObject();
        prop.put("kind", option.getKind());
        prop.put("displayName", option.getDisplayName());
        prop.put("group", option.getGroup());
        prop.put("label", option.getLabel());
        prop.put("required", option.isRequired());
        prop.put("type", option.getType());
        prop.put("javaType", option.getJavaType());
        prop.put("enums", option.getEnums());
        prop.put("oneOfs", option.getOneOfs());
        prop.put("prefix", option.getPrefix());
        prop.put("optionalPrefix", option.getOptionalPrefix());
        prop.put("multiValue", option.isMultiValue());
        prop.put("deprecated", option.isDeprecated());
        prop.put("deprecationNote", option.getDeprecationNote());
        prop.put("secret", option.isSecret());
        prop.put("defaultValue", option.getDefaultValue());
        prop.put("asPredicate", option.isAsPredicate());
        prop.put("configurationClass", option.getConfigurationClass());
        prop.put("configurationField", option.getConfigurationField());
        prop.put("description", option.getDescription());
        prop.entrySet().removeIf(e -> e.getValue() == null);
        return prop;
    }

    protected static JsonObject deserialize(String json) {
        try {
            return (JsonObject) Jsoner.deserialize(json);
        } catch (Exception e) {
            // wrap parsing exceptions as runtime
            throw new RuntimeException("Cannot parse json", e);
        }
    }

    protected static List<String> asStringList(Collection<?> col) {
        if (col != null) {
            return col.stream().map(Object::toString).collect(Collectors.toList());
        } else {
            return null;
        }
    }

}
