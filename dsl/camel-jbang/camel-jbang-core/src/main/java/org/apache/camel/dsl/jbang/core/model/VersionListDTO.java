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

public class VersionListDTO {

    private String camelVersion;
    private String runtime;
    private String runtimeVersion;
    private String jdkVersion;
    private String kind;
    private String releaseDate;
    private String eolDate;

    public VersionListDTO() {}

    public VersionListDTO(
            String camelVersion,
            String runtime,
            String runtimeVersion,
            String jdkVersion,
            String kind,
            String releaseDate,
            String eolDate) {
        this.camelVersion = camelVersion;
        this.runtime = runtime;
        this.runtimeVersion = runtimeVersion;
        this.jdkVersion = jdkVersion;
        this.kind = kind;
        this.releaseDate = releaseDate;
        this.eolDate = eolDate;
    }

    public String getCamelVersion() {
        return camelVersion;
    }

    public void setCamelVersion(String camelVersion) {
        this.camelVersion = camelVersion;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public void setJdkVersion(String jdkVersion) {
        this.jdkVersion = jdkVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getEolDate() {
        return eolDate;
    }

    public void setEolDate(String eolDate) {
        this.eolDate = eolDate;
    }

    public Map<String, Object> toMap() {
        JsonObject jo = new JsonObject();
        jo.put("camelVersion", camelVersion);
        jo.put("runtime", runtime);
        if (runtimeVersion != null) {
            jo.put("runtimeVersion", runtimeVersion);
        }
        if (jdkVersion != null) {
            jo.put("jdkVersion", jdkVersion);
        }
        if (kind != null) {
            jo.put("kind", kind);
        }
        if (releaseDate != null) {
            jo.put("releaseDate", releaseDate);
        }
        if (eolDate != null) {
            jo.put("eolDate", eolDate);
        }
        return jo;
    }
}
