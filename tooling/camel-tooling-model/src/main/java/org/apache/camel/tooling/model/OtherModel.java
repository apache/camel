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

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public class OtherModel extends BaseModel<BaseOptionModel> {

    protected String groupId;
    protected String artifactId;
    protected String version;

    public static OtherModel generateOtherModel(String json) {
        JsonObject obj = deserialize(json);
        JsonObject mobj = (JsonObject) obj.get("other");
        OtherModel model = new OtherModel();
        parseModel(mobj, model);
        model.setGroupId(mobj.getString("groupId"));
        model.setArtifactId(mobj.getString("artifactId"));
        model.setVersion(mobj.getString("version"));
        return model;
    }

    public static String createJsonSchema(OtherModel model) {
        JsonObject obj = new JsonObject();
        obj.put("kind", "other");
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
        return Jsoner.prettyPrint(Jsoner.serialize(wrapper), 2, 2);
    }

    public OtherModel() {
        setKind("other");
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
