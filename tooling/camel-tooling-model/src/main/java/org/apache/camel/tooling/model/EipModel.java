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

import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public class EipModel extends BaseModel<EipModel.EipOptionModel> {

    protected boolean input;          // used in models from camel-core-engine
    protected boolean output;         // used in models from camel-core-engine

    public static EipModel generateEipModel(String json) {
        JsonObject obj = deserialize(json);
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
        return Jsoner.prettyPrint(Jsoner.serialize(wrapper), 2, 2);
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
        prop.put("multiValue", option.isMultiValue() ? Boolean.TRUE : null);
        prop.put("deprecated", option.isDeprecated());
        prop.put("deprecationNote", option.getDeprecationNote());
        prop.put("secret", option.isSecret());
        prop.put("defaultValue", option.getDefaultValue());
        prop.put("asPredicate", option.isAsPredicate() ? Boolean.TRUE : null);
        prop.put("configurationClass", option.getConfigurationClass());
        prop.put("configurationField", option.getConfigurationField());
        prop.put("description", option.getDescription());
        prop.entrySet().removeIf(e -> e.getValue() == null);
        return prop;
    }

    public EipModel() {
        setKind("model");
    }

    public boolean isInput() {
        return input;
    }

    public void setInput(boolean input) {
        this.input = input;
    }

    public boolean isOutput() {
        return output;
    }

    public void setOutput(boolean output) {
        this.output = output;
    }

    public String getDocLink() {
        // lets store EIP docs in a sub-folder as we have many EIPs
        return "src/main/docs/eips/";
    }

    public static class EipOptionModel extends BaseOptionModel {

    }
}
