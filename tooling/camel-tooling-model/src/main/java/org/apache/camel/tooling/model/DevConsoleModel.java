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

public class DevConsoleModel extends ArtifactModel<DevConsoleModel.DevConsoleOptionModel> {

    protected String group;
    protected boolean readOnly = true;
    protected JsonObject responseSchema;

    public DevConsoleModel() {
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * The JSON Schema describing this console's response payload, or {@code null} if the console has not declared one
     * (via a nested {@code Response} record).
     */
    public JsonObject getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(JsonObject responseSchema) {
        this.responseSchema = responseSchema;
    }

    @Override
    public Kind getKind() {
        return Kind.console;
    }

    public static class DevConsoleOptionModel extends BaseOptionModel {
    }

}
