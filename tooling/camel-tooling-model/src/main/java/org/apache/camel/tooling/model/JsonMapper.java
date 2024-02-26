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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ComponentModel.ComponentOptionModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointHeaderModel;
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
            String json = Files.readString(file);
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
        } else if (obj.containsKey("transformer")) {
            return generateTransformerModel(obj);
        } else if (obj.containsKey("other")) {
            return generateOtherModel(obj);
        } else if (obj.containsKey("model")) {
            return generateEipModel(obj);
        } else {
            return null;
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
        JsonObject headers = (JsonObject) obj.get("headers");
        if (headers != null) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                JsonObject mp = (JsonObject) entry.getValue();
                EndpointHeaderModel header = new EndpointHeaderModel();
                parseOption(mp, header, entry.getKey());
                header.setConstantName(mp.getString("constantName"));
                model.addEndpointHeader(header);
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
        JsonObject mprap = (JsonObject) obj.get("apis");
        if (mprap != null) {
            for (Map.Entry<String, Object> entry : mprap.entrySet()) {
                String name = entry.getKey();
                JsonObject mp = (JsonObject) entry.getValue();
                ApiModel am = new ApiModel();
                am.setName(name);
                am.setDescription(mp.getStringOrDefault("description", ""));
                am.setConsumerOnly(mp.getBooleanOrDefault("consumerOnly", false));
                am.setProducerOnly(mp.getBooleanOrDefault("producerOnly", false));
                model.getApiOptions().add(am);
                Collection<String> aliases = mp.getCollection("aliases");
                if (aliases != null && !aliases.isEmpty()) {
                    aliases.forEach(am::addAlias);
                }
                JsonObject mm = (JsonObject) mp.get("methods");
                if (mm != null) {
                    for (Map.Entry<String, Object> mme : mm.entrySet()) {
                        JsonObject mmp = (JsonObject) mme.getValue();
                        ApiMethodModel amm = am.newMethod(mme.getKey());
                        Collection<String> signatures = mmp.getCollection("signatures");
                        if (signatures != null && !signatures.isEmpty()) {
                            signatures.forEach(amm::addSignature);
                        }
                        amm.setDescription(mmp.getStringOrDefault("description", ""));
                    }
                }
            }
        }
        mprap = (JsonObject) obj.get("apiProperties");
        if (mprap != null) {
            for (Map.Entry<String, Object> entry : mprap.entrySet()) {
                JsonObject mp = (JsonObject) entry.getValue();
                String name = entry.getKey();
                ApiModel am = model.getApiOptions().stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
                if (am == null) {
                    throw new RuntimeException("Invalid json. Cannot find ApiModel with name: " + name);
                }
                JsonObject mm = (JsonObject) mp.get("methods");
                if (mm != null) {
                    for (Map.Entry<String, Object> mme : mm.entrySet()) {
                        JsonObject mmp = (JsonObject) mme.getValue();
                        String mname = mme.getKey();
                        ApiMethodModel amm
                                = am.getMethods().stream().filter(a -> a.getName().equals(mname)).findFirst().orElse(null);
                        if (amm == null) {
                            throw new RuntimeException("Invalid json. Cannot find ApiMethodModel with name: " + mname);
                        }
                        JsonObject properties = (JsonObject) mmp.get("properties");
                        if (properties != null) {
                            for (Map.Entry<String, Object> pe : properties.entrySet()) {
                                JsonObject prop = (JsonObject) pe.getValue();
                                ComponentModel.ApiOptionModel option = new ComponentModel.ApiOptionModel();
                                parseOption(prop, option, pe.getKey());
                                option.setOptional(prop.getBooleanOrDefault("optional", false));
                                amm.addApiOptionModel(option);
                            }
                        }
                    }
                }
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
        model.setApi(mobj.getBooleanOrDefault("api", false));
        model.setApiSyntax(mobj.getString("apiSyntax"));
        model.setConsumerOnly(mobj.getBooleanOrDefault("consumerOnly", false));
        model.setProducerOnly(mobj.getBooleanOrDefault("producerOnly", false));
        model.setLenientProperties(mobj.getBooleanOrDefault("lenientProperties", false));
        model.setRemote(mobj.getBooleanOrDefault("remote", false));
        parseArtifact(mobj, model);
    }

    private static void parseArtifact(JsonObject mobj, ArtifactModel<?> model) {
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
        baseToJson(model, obj);
        artifactToJson(model, obj);
        obj.put("scheme", model.getScheme());
        obj.put("extendsScheme", model.getExtendsScheme());
        obj.put("alternativeSchemes", model.getAlternativeSchemes());
        obj.put("syntax", model.getSyntax());
        obj.put("alternativeSyntax", model.getAlternativeSyntax());
        obj.put("async", model.isAsync());
        obj.put("api", model.isApi());
        if (model.isApi()) {
            obj.put("apiSyntax", model.getApiSyntax());
        }
        obj.put("consumerOnly", model.isConsumerOnly());
        obj.put("producerOnly", model.isProducerOnly());
        obj.put("lenientProperties", model.isLenientProperties());
        obj.put("remote", model.isRemote());
        obj.put("verifiers", model.getVerifiers());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("component", obj);
        wrapper.put("componentProperties", asJsonObject(model.getComponentOptions()));
        final List<EndpointHeaderModel> headers = model.getEndpointHeaders();
        if (!headers.isEmpty()) {
            wrapper.put("headers", asJsonObject(headers));
        }
        wrapper.put("properties", asJsonObject(model.getEndpointOptions()));
        if (!model.getApiOptions().isEmpty()) {
            wrapper.put("apis", apiModelAsJsonObject(model.getApiOptions(), false));
            wrapper.put("apiProperties", apiModelAsJsonObject(model.getApiOptions(), true));
        }
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
        parseArtifact(mobj, model);
        model.setModelName(mobj.getString("modelName"));
        model.setModelJavaType(mobj.getString("modelJavaType"));
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
        baseToJson(model, obj);
        artifactToJson(model, obj);

        obj.put("modelName", model.getModelName());
        obj.put("modelJavaType", model.getModelJavaType());
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
        model.setAbstractModel(mobj.getBooleanOrDefault("abstract", false));
        model.setInput(mobj.getBooleanOrDefault("input", false));
        model.setOutput(mobj.getBooleanOrDefault("output", false));
        JsonObject mprp = (JsonObject) obj.get("properties");
        if (mprp != null) {
            for (Map.Entry<String, Object> entry : mprp.entrySet()) {
                JsonObject mp = (JsonObject) entry.getValue();
                EipOptionModel option = new EipOptionModel();
                parseOption(mp, option, entry.getKey());
                model.addOption(option);
            }
        }
        mprp = (JsonObject) obj.get("exchangeProperties");
        if (mprp != null) {
            for (Map.Entry<String, Object> entry : mprp.entrySet()) {
                JsonObject mp = (JsonObject) entry.getValue();
                EipOptionModel option = new EipOptionModel();
                parseOption(mp, option, entry.getKey());
                model.addExchangeProperty(option);
            }
        }
        return model;
    }

    public static String createParameterJsonSchema(EipModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(EipModel model) {
        JsonObject obj = new JsonObject();
        baseToJson(model, obj);
        obj.put("abstract", model.isAbstractModel());
        obj.put("input", model.isInput());
        obj.put("output", model.isOutput());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("model", obj);
        wrapper.put("properties", asJsonObject(model.getOptions()));
        if (!model.getExchangeProperties().isEmpty()) {
            wrapper.put("exchangeProperties", asJsonObject(model.getExchangeProperties()));
        }
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
        parseArtifact(mobj, model);
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
        baseToJson(model, obj);
        artifactToJson(model, obj);
        obj.put("modelName", model.getModelName());
        obj.put("modelJavaType", model.getModelJavaType());
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("language", obj);
        wrapper.put("properties", asJsonObject(model.getOptions()));
        return wrapper;
    }

    public static TransformerModel generateTransformerModel(String json) {
        JsonObject obj = deserialize(json);
        return generateTransformerModel(obj);
    }

    public static TransformerModel generateTransformerModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("transformer");
        TransformerModel model = new TransformerModel();
        parseModel(mobj, model);
        model.setFrom(mobj.getString("from"));
        model.setTo(mobj.getString("to"));
        parseArtifact(mobj, model);
        return model;
    }

    public static OtherModel generateOtherModel(String json) {
        JsonObject obj = deserialize(json);
        return generateOtherModel(obj);
    }

    public static OtherModel generateOtherModel(JsonObject obj) {
        JsonObject mobj = (JsonObject) obj.get("other");
        OtherModel model = new OtherModel();
        parseModel(mobj, model);
        parseArtifact(mobj, model);
        return model;
    }

    public static String createJsonSchema(OtherModel model) {
        JsonObject wrapper = asJsonObject(model);
        return serialize(wrapper);
    }

    public static JsonObject asJsonObject(OtherModel model) {
        JsonObject obj = new JsonObject();
        baseToJson(model, obj);
        artifactToJson(model, obj);
        obj.entrySet().removeIf(e -> e.getValue() == null);
        JsonObject wrapper = new JsonObject();
        wrapper.put("other", obj);
        return wrapper;
    }

    private static void baseToJson(BaseModel<?> model, JsonObject obj) {
        obj.put("kind", model.getKind());
        obj.put("name", model.getName());
        obj.put("title", model.getTitle());
        obj.put("description", model.getDescription());
        obj.put("deprecated", model.isDeprecated());
        obj.put("deprecatedSince", model.getDeprecatedSince());
        obj.put("deprecationNote", model.getDeprecationNote());
        obj.put("firstVersion", model.getFirstVersion());
        obj.put("label", model.getLabel());
        obj.put("javaType", model.getJavaType());
        if (model.getSupportLevel() != null) {
            obj.put("supportLevel", model.getSupportLevel().name());
        }
        if (model.isNativeSupported()) {
            obj.put("nativeSupported", model.isNativeSupported());
        }
        if (!model.getMetadata().isEmpty()) {
            obj.put("metadata", model.getMetadata());
        }
    }

    private static void artifactToJson(ArtifactModel<?> model, JsonObject obj) {
        obj.put("groupId", model.getGroupId());
        obj.put("artifactId", model.getArtifactId());
        obj.put("version", model.getVersion());
    }

    private static void parseModel(JsonObject mobj, BaseModel<?> model) {
        model.setTitle(mobj.getString("title"));
        model.setName(mobj.getString("name"));
        model.setDescription(mobj.getString("description"));
        model.setFirstVersion(mobj.getString("firstVersion"));
        model.setLabel(mobj.getString("label"));
        model.setDeprecated(mobj.getBooleanOrDefault("deprecated", false));
        model.setDeprecatedSince(mobj.getString("deprecatedSince"));
        model.setDeprecationNote(mobj.getString("deprecationNote"));
        model.setJavaType(mobj.getString("javaType"));
        model.setSupportLevel(SupportLevel.safeValueOf(mobj.getString("supportLevel")));
        model.setNativeSupported(mobj.getBooleanOrDefault("nativeSupported", false));
        model.setMetadata(mobj.getMapOrDefault("metadata", new JsonObject()));
    }

    private static void parseOption(JsonObject mp, BaseOptionModel option, String name) {
        option.setName(name);
        Integer idx = mp.getInteger("index");
        if (idx != null) {
            option.setIndex(idx);
        }
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
        option.setAutowired(mp.getBooleanOrDefault("autowired", false));
        option.setDeprecationNote(mp.getString("deprecationNote"));
        option.setSecret(mp.getBooleanOrDefault("secret", false));
        option.setDefaultValue(mp.get("defaultValue"));
        option.setAsPredicate(mp.getBooleanOrDefault("asPredicate", false));
        option.setConfigurationClass(mp.getString("configurationClass"));
        option.setConfigurationField(mp.getString("configurationField"));
        option.setDescription(mp.getString("description"));
        option.setGetterMethod(mp.getString("getterMethod"));
        option.setSetterMethod(mp.getString("setterMethod"));
        option.setSupportFileReference(mp.getBooleanOrDefault("supportFileReference", false));
        option.setLargeInput(mp.getBooleanOrDefault("largeInput", false));
        option.setInputLanguage(mp.getString("inputLanguage"));
    }

    private static void parseGroup(JsonObject mp, MainGroupModel option) {
        option.setName(mp.getString("name"));
        option.setDescription(mp.getString("description"));
        option.setSourceType(mp.getString("sourceType"));
    }

    public static JsonObject asJsonObject(List<? extends BaseOptionModel> options) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < options.size(); i++) {
            var o = options.get(i);
            o.setIndex(i);
            json.put(o.getName(), asJsonObject(o));
        }
        return json;
    }

    public static JsonObject apiModelAsJsonObject(Collection<ApiModel> model, boolean options) {
        JsonObject root = new JsonObject();
        model.forEach(a -> {
            JsonObject json = new JsonObject();
            root.put(a.getName(), json);
            if (!options) {
                // lets be less verbose and only output these details for the api summary and not when we have all options included
                json.put("consumerOnly", a.isConsumerOnly());
                json.put("producerOnly", a.isProducerOnly());
                if (a.getDescription() != null) {
                    json.put("description", a.getDescription());
                }
                if (!a.getAliases().isEmpty()) {
                    json.put("aliases", new JsonArray(a.getAliases()));
                }
            }
            Map<String, JsonObject> methods = new TreeMap<>();
            json.put("methods", methods);
            a.getMethods().forEach(m -> {
                JsonObject mJson = new JsonObject();
                if (!options) {
                    // lets be less verbose and only output these details for the api summary and not when we have all options included
                    if (m.getDescription() != null) {
                        mJson.put("description", m.getDescription());
                    }
                    if (!m.getSignatures().isEmpty()) {
                        mJson.put("signatures", new JsonArray(m.getSignatures()));
                    }
                }
                if (options) {
                    mJson.put("properties", asJsonObject(m.getOptions()));
                }
                methods.put(m.getName(), mJson);
            });
        });
        return root;
    }

    public static JsonObject asJsonObject(BaseOptionModel option) {
        JsonObject prop = new JsonObject();
        prop.put("index", option.getIndex());
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
        prop.put("autowired", option.isAutowired());
        prop.put("secret", option.isSecret());
        prop.put("defaultValue", option.getDefaultValue());
        if (option.isSupportFileReference()) {
            // only include if supported to not regen all files
            prop.put("supportFileReference", option.isSupportFileReference());
        }
        if (option.isLargeInput()) {
            // only include if supported to not regen all files
            prop.put("largeInput", option.isLargeInput());
        }
        if (!Strings.isNullOrEmpty(option.getInputLanguage())) {
            // only include if supported to not regen all files
            prop.put("inputLanguage", option.getInputLanguage());
        }
        prop.put("asPredicate", option.isAsPredicate());
        prop.put("configurationClass", option.getConfigurationClass());
        prop.put("configurationField", option.getConfigurationField());
        prop.put("description", option.getDescription());
        prop.put("getterMethod", option.getGetterMethod());
        prop.put("setterMethod", option.getSetterMethod());
        if (option instanceof ComponentModel.ApiOptionModel) {
            prop.put("optional", ((ComponentModel.ApiOptionModel) option).isOptional());
        } else if (option instanceof ComponentModel.EndpointHeaderModel) {
            prop.put("constantName", ((ComponentModel.EndpointHeaderModel) option).getConstantName());
        }
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
            if (prop.isAutowired()) {
                j.put("autowired", prop.isAutowired());
            }
            props.add(j);
        }
        json.put("properties", props);
        return json;
    }

    public static JsonObject asJsonObject(ReleaseModel model) {
        JsonObject json = new JsonObject();
        json.put("version", model.getVersion());
        json.put("date", model.getDate());
        if (model.getEol() != null) {
            json.put("eol", model.getEol());
        }
        if (model.getKind() != null) {
            json.put("kind", model.getKind());
        }
        if (model.getJdk() != null) {
            json.put("jdk", model.getJdk());
        }
        return json;
    }

    public static ReleaseModel generateReleaseModel(JsonObject obj) {
        ReleaseModel model = new ReleaseModel();
        model.setVersion(obj.getString("version"));
        model.setDate(obj.getString("date"));
        model.setEol(obj.getString("eol"));
        model.setKind(obj.getString("kind"));
        model.setJdk(obj.getString("jdk"));
        return model;
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
