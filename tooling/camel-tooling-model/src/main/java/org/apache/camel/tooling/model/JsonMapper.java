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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ComponentModel.ComponentOptionModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.DataFormatModel.DataFormatOptionModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.LanguageModel.LanguageOptionModel;
import org.apache.camel.tooling.model.MainModel.MainGroupModel;
import org.apache.camel.tooling.model.MainModel.MainOptionModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public final class JsonMapper {

    private JsonMapper() {
    }

    public static BaseModel<?> generateModel(Path file) {
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return generateModel(json);
        } catch (IOException e) {
            throw new RuntimeException("Error reading json file: " + file, e);
        }
    }

    public static BaseModel<?> generateModel(String json) {
        JsonObject obj = deserialize(json);
        return generateModel(obj);
    }

    public static BaseModel<?> generateModel(JsonObject obj) {
        if (obj.containsKey("component")) {
            return generateComponentModel(obj);
        } else if (obj.containsKey("language")) {
            return generateLanguageModel(obj);
        } else if (obj.containsKey("dataformat")) {
            return generateDataFormatModel(obj);
        } else if (obj.containsKey("other")) {
            return generateOtherModel(obj);
        } else if (obj.containsKey("model")) {
            return generateEipModel(obj);
        } else {
            throw new IllegalArgumentException("Unsupported JSON");
        }
    }

    public static ComponentModel generateComponentModel(String json) {
        JsonObject obj = deserialize(json);
        return generateComponentModel(obj);
    }

    public static ComponentModel generateComponentModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("component");
        ComponentModel model = new ComponentModel();
        parseComponentModel(mobj, model);
        JsonObject mcprp = (JsonObject) obj.get("componentProperties");
        if (mcprp != null) {
            for (Map.Entry<String, Object> entry : mcprp.entrySet()) {
                JsonObject mp = (JsonObject) entry.getValue();
                ComponentOptionModel option = new ComponentOptionModel();
                parseOption(mp, option, entry.getKey());
                model.addComponentOption(option);
            }
        }
        JsonObject mprp = (JsonObject) obj.get("properties");
        if (mprp != null) {
            for (Map.Entry<String, Object> entry : mprp.entrySet()) {
                JsonObject mp = (JsonObject) entry.getValue();
                EndpointOptionModel option = new EndpointOptionModel();
                parseOption(mp, option, entry.getKey());
                model.addEndpointOption(option);
            }
        }
        return model;
    }

    public static void parseComponentModel(JsonObject mobj, ComponentModel model) {
        parseModel(mobj, model);
        model.setScheme(mobj.getString("scheme"));
        model.setExtendsScheme(mobj.getString("extendsScheme"));
        model.setAlternativeSchemes(mobj.getString("alternativeSchemes"));
        model.setSyntax(mobj.getString("syntax"));
        model.setAlternativeSyntax(mobj.getString("alternativeSyntax"));
        model.setAsync(mobj.getBooleanOrDefault("async", false));
        model.setConsumerOnly(mobj.getBooleanOrDefault("consumerOnly", false));
        model.setProducerOnly(mobj.getBooleanOrDefault("producerOnly", false));
        model.setLenientProperties(mobj.getBooleanOrDefault("lenientProperties", false));
        model.setGroupId(mobj.getString("groupId"));
        model.setArtifactId(mobj.getString("artifactId"));
        model.setVersion(mobj.getString("version"));
    }

    public static String createParameterJsonSchema(ComponentModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(ComponentModel model) {
        JsonObject obj = new JsonObject();
        obj.put("kind", model.getKind());
        obj.put("name", model.getName());
        obj.put("scheme", model.getScheme());
        obj.put("extendsScheme", model.getExtendsScheme());
        obj.put("alternativeSchemes", model.getAlternativeSchemes());
        obj.put("syntax", model.getSyntax());
        obj.put("alternativeSyntax", model.getAlternativeSyntax());
        obj.put("title", model.getTitle());
        obj.put("description", model.getDescription());
        obj.put("label", model.getLabel());
        obj.put("deprecated", model.isDeprecated());
        obj.put("deprecationNote", model.getDeprecationNote());
        obj.put("async", model.isAsync());
        obj.put("consumerOnly", model.isConsumerOnly());
        obj.put("producerOnly", model.isProducerOnly());
        obj.put("lenientProperties", model.isLenientProperties());
        obj.put("javaType", model.getJavaType());
        obj.put("firstVersion", model.getFirstVersion());
        obj.put("verifiers", model.getVerifiers());
        obj.put("groupId", model.getGroupId());
        obj.put("artifactId", model.getArtifactId());
        obj.put("version", model.getVersion());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("component", obj);
        wrapper.put("componentProperties", asJsonObject(model.getComponentOptions()));
        wrapper.put("properties", asJsonObject(model.getEndpointOptions()));
        return wrapper;
    }

    public static DataFormatModel generateDataFormatModel(String json) {
        JsonObject obj = deserialize(json);
        return generateDataFormatModel(obj);
    }

    public static DataFormatModel generateDataFormatModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("dataformat");
        DataFormatModel model = new DataFormatModel();
        parseModel(mobj, model);
        model.setModelName(mobj.getString("modelName"));
        model.setModelJavaType(mobj.getString("modelJavaType"));
        model.setGroupId(mobj.getString("groupId"));
        model.setArtifactId(mobj.getString("artifactId"));
        model.setVersion(mobj.getString("version"));
        JsonObject mprp = (JsonObject) obj.get("properties");
        for (Map.Entry<String, Object> entry : mprp.entrySet()) {
            JsonObject mp = (JsonObject) entry.getValue();
            DataFormatOptionModel option = new DataFormatOptionModel();
            parseOption(mp, option, entry.getKey());
            model.addOption(option);
        }
        return model;
    }

    public static String createParameterJsonSchema(DataFormatModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(DataFormatModel model) {
        JsonObject obj = new JsonObject();
        obj.put("kind", model.getKind());
        obj.put("name", model.getName());
        obj.put("modelName", model.getModelName());
        obj.put("title", model.getTitle());
        obj.put("description", model.getDescription());
        obj.put("deprecated", model.isDeprecated());
        obj.put("deprecationNote", model.getDeprecationNote());
        obj.put("firstVersion", model.getFirstVersion());
        obj.put("label", model.getLabel());
        obj.put("javaType", model.getJavaType());
        obj.put("modelJavaType", model.getModelJavaType());
        obj.put("groupId", model.getGroupId());
        obj.put("artifactId", model.getArtifactId());
        obj.put("version", model.getVersion());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("dataformat", obj);
        wrapper.put("properties", asJsonObject(model.getOptions()));
        return wrapper;
    }

    public static EipModel generateEipModel(String json) {
        JsonObject obj = deserialize(json);
        return generateEipModel(obj);
    }

    public static EipModel generateEipModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("model");
        EipModel model = new EipModel();
        parseModel(mobj, model);
        model.setInput(mobj.getBooleanOrDefault("input", false));
        model.setOutput(mobj.getBooleanOrDefault("output", false));
        JsonObject mprp = (JsonObject) obj.get("properties");
        for (Map.Entry<String, Object> entry : mprp.entrySet()) {
            JsonObject mp = (JsonObject) entry.getValue();
            EipOptionModel option = new EipOptionModel();
            parseOption(mp, option, entry.getKey());
            model.addOption(option);
        }
        return model;
    }

    public static String createParameterJsonSchema(EipModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(EipModel model) {
        JsonObject obj = new JsonObject();
        obj.put("kind", model.getKind());
        obj.put("name", model.getName());
        obj.put("title", model.getTitle());
        obj.put("description", model.getDescription());
        obj.put("firstVersion", model.getFirstVersion());
        obj.put("javaType", model.getJavaType());
        obj.put("label", model.getLabel());
        obj.put("deprecated", model.isDeprecated());
        obj.put("deprecationNote", model.getDeprecationNote());
        obj.put("input", model.isInput());
        obj.put("output", model.isOutput());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("model", obj);
        wrapper.put("properties", asJsonObject(model.getOptions()));
        return wrapper;
    }

    public static LanguageModel generateLanguageModel(String json) {
        JsonObject obj = deserialize(json);
        return generateLanguageModel(obj);
    }

    public static LanguageModel generateLanguageModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("language");
        LanguageModel model = new LanguageModel();
        parseModel(mobj, model);
        model.setModelName(mobj.getString("modelName"));
        model.setModelJavaType(mobj.getString("modelJavaType"));
        model.setGroupId(mobj.getString("groupId"));
        model.setArtifactId(mobj.getString("artifactId"));
        model.setVersion(mobj.getString("version"));
        JsonObject mprp = (JsonObject) obj.get("properties");
        for (Map.Entry<String, Object> entry : mprp.entrySet()) {
            JsonObject mp = (JsonObject) entry.getValue();
            LanguageOptionModel option = new LanguageOptionModel();
            parseOption(mp, option, entry.getKey());
            model.addOption(option);
        }
        return model;
    }

    public static String createParameterJsonSchema(LanguageModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(LanguageModel model) {
        JsonObject obj = new JsonObject();
        obj.put("kind", model.getKind());
        obj.put("name", model.getName());
        obj.put("modelName", model.getModelName());
        obj.put("title", model.getTitle());
        obj.put("description", model.getDescription());
        obj.put("deprecated", model.isDeprecated());
        obj.put("deprecationNote", model.getDeprecationNote());
        obj.put("firstVersion", model.getFirstVersion());
        obj.put("label", model.getLabel());
        obj.put("javaType", model.getJavaType());
        obj.put("modelJavaType", model.getModelJavaType());
        obj.put("groupId", model.getGroupId());
        obj.put("artifactId", model.getArtifactId());
        obj.put("version", model.getVersion());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("language", obj);
        wrapper.put("properties", asJsonObject(model.getOptions()));
        return wrapper;
    }

    public static OtherModel generateOtherModel(String json) {
        JsonObject obj = deserialize(json);
        return generateOtherModel(obj);
    }

    public static OtherModel generateOtherModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("other");
        OtherModel model = new OtherModel();
        parseModel(mobj, model);
        model.setGroupId(mobj.getString("groupId"));
        model.setArtifactId(mobj.getString("artifactId"));
        model.setVersion(mobj.getString("version"));
        return model;
    }

    public static String createJsonSchema(OtherModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(OtherModel model) {
        JsonObject obj = new JsonObject();
        obj.put("kind", model.getKind());
        obj.put("name", model.getName());
        obj.put("title", model.getTitle());
        obj.put("description", model.getDescription());
        obj.put("deprecated", model.isDeprecated());
        obj.put("deprecationNote", model.getDeprecationNote());
        obj.put("firstVersion", model.getFirstVersion());
        obj.put("label", model.getLabel());
        obj.put("groupId", model.getGroupId());
        obj.put("artifactId", model.getArtifactId());
        obj.put("version", model.getVersion());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("other", obj);
        return wrapper;
    }

    private static void parseModel(JsonObject mobj, BaseModel<?> model) {
        model.setTitle(mobj.getString("title"));
        model.setName(mobj.getString("name"));
        model.setDescription(mobj.getString("description"));
        model.setFirstVersion(mobj.getString("firstVersion"));
        model.setLabel(mobj.getString("label"));
        model.setDeprecated(mobj.getBooleanOrDefault("deprecated", false));
        model.setDeprecationNote(mobj.getString("label"));
        model.setJavaType(mobj.getString("javaType"));
    }

    private static void parseOption(JsonObject mp, BaseOptionModel option, String name) {
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

    private static void parseGroup(JsonObject mp, MainGroupModel option) {
        option.setName(mp.getString("name"));
        option.setDescription(mp.getString("description"));
        option.setSourceType(mp.getString("sourceType"));
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
        prop.put("enum", option.getEnums());
        prop.put("oneOf", option.getOneOfs());
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
        prop.remove("prefix", "");
        prop.remove("optionalPrefix", "");
        prop.remove("defaultValue", "");
        prop.remove("multiValue", Boolean.FALSE);
        prop.remove("asPredicate", Boolean.FALSE);
        return prop;
    }

    public static MainModel generateMainModel(String json) {
        JsonObject obj = deserialize(json);
        return generateMainModel(obj);
    }

    public static MainModel generateMainModel(JsonObject obj) {
        MainModel model = new MainModel();
        JsonArray mgrp = (JsonArray) obj.get("groups");
        for (Object entry : mgrp) {
            JsonObject mg = (JsonObject) entry;
            MainGroupModel group = new MainGroupModel();
            parseGroup(mg, group);
            model.addGroup(group);
        }
        JsonArray mprp = (JsonArray) obj.get("properties");
        for (Object entry : mprp) {
            JsonObject mp = (JsonObject) entry;
            MainOptionModel option = new MainOptionModel();
            parseOption(mp, option, mp.getString("name"));
            option.setSourceType(mp.getString("sourceType"));
            model.addOption(option);
        }
        return model;
    }

    public static JsonObject asJsonObject(MainModel model) {
        JsonObject json = new JsonObject();
        JsonArray groups = new JsonArray();
        for (MainGroupModel group : model.getGroups()) {
            JsonObject j = new JsonObject();
            j.put("name", group.getName());
            j.put("description", group.getDescription());
            j.put("sourceType", group.getSourceType());
            groups.add(j);
        }
        json.put("groups", groups);
        JsonArray props = new JsonArray();
        for (MainOptionModel prop : model.getOptions()) {
            JsonObject j = new JsonObject();
            j.put("name", prop.getName());
            j.put("description", prop.getDescription());
            j.put("sourceType", prop.getSourceType());
            j.put("type", prop.getType());
            j.put("javaType", prop.getJavaType());
            if (prop.getDefaultValue() != null) {
                j.put("defaultValue", prop.getDefaultValue());
            }
            if (prop.getEnums() != null) {
                j.put("enum", prop.getEnums());
            }
            if (prop.isDeprecated()) {
                j.put("deprecated", prop.isDeprecated());
            }
            props.add(j);
        }
        json.put("properties", props);
        return json;
    }

    public static String createJsonSchema(MainModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject deserialize(String json) {
        try {
            return (JsonObject) Jsoner.deserialize(json);
        } catch (Exception e) {
            // wrap parsing exceptions as runtime
            throw new RuntimeException("Cannot parse json", e);
        }
    }

    public static String serialize(Object json) {
        return Jsoner.prettyPrint(Jsoner.serialize(json), 2, 2);
    }

    protected static List<String> asStringList(Collection<?> col) {
        if (col != null) {
            return col.stream().map(Object::toString).collect(Collectors.toList());
        } else {
            return null;
        }
    }
}
