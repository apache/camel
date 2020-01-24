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

import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public class LanguageModel extends BaseModel<LanguageModel.LanguageOptionModel> {

    protected String modelName;
    protected String modelJavaType;
    protected String groupId;
    protected String artifactId;
    protected String version;

    public static LanguageModel generateLanguageModel(String json) {
        JsonObject obj = deserialize(json);
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
        return Jsoner.prettyPrint(Jsoner.serialize(wrapper), 2, 2);
    }

    public static class LanguageOptionModel extends BaseOptionModel {

    }

    public LanguageModel() {
        setKind("language");
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelJavaType() {
        return modelJavaType;
    }

    public void setModelJavaType(String modelJavaType) {
        this.modelJavaType = modelJavaType;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
