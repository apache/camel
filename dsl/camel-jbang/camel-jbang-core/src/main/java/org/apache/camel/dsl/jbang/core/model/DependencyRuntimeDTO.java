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

public class DependencyRuntimeDTO {

    private String runtime;
    private String camelVersion;
    private String camelSpringBootVersion;
    private String camelQuarkusVersion;
    private String springBootVersion;
    private String quarkusVersion;
    private String camelSpringBootBomGroupId;
    private String camelSpringBootBomArtifactId;
    private String quarkusBomGroupId;
    private String quarkusBomArtifactId;
    private String camelQuarkusBomGroupId;
    private String camelQuarkusBomArtifactId;

    public DependencyRuntimeDTO() {
    }

    public DependencyRuntimeDTO(String runtime, String camelVersion, String camelSpringBootVersion, String camelQuarkusVersion,
                                String springBootVersion, String quarkusVersion, String camelSpringBootBomGroupId,
                                String camelSpringBootBomArtifactId, String quarkusBomGroupId, String quarkusBomArtifactId,
                                String camelQuarkusBomGroupId, String camelQuarkusBomArtifactId) {
        this.runtime = runtime;
        this.camelVersion = camelVersion;
        this.camelSpringBootVersion = camelSpringBootVersion;
        this.camelQuarkusVersion = camelQuarkusVersion;
        this.springBootVersion = springBootVersion;
        this.quarkusVersion = quarkusVersion;
        this.camelSpringBootBomGroupId = camelSpringBootBomGroupId;
        this.camelSpringBootBomArtifactId = camelSpringBootBomArtifactId;
        this.quarkusBomGroupId = quarkusBomGroupId;
        this.quarkusBomArtifactId = quarkusBomArtifactId;
        this.camelQuarkusBomGroupId = camelQuarkusBomGroupId;
        this.camelQuarkusBomArtifactId = camelQuarkusBomArtifactId;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getCamelVersion() {
        return camelVersion;
    }

    public void setCamelVersion(String camelVersion) {
        this.camelVersion = camelVersion;
    }

    public String getCamelSpringBootVersion() {
        return camelSpringBootVersion;
    }

    public void setCamelSpringBootVersion(String camelSpringBootVersion) {
        this.camelSpringBootVersion = camelSpringBootVersion;
    }

    public String getCamelQuarkusVersion() {
        return camelQuarkusVersion;
    }

    public void setCamelQuarkusVersion(String camelQuarkusVersion) {
        this.camelQuarkusVersion = camelQuarkusVersion;
    }

    public String getSpringBootVersion() {
        return springBootVersion;
    }

    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }

    public String getQuarkusVersion() {
        return quarkusVersion;
    }

    public void setQuarkusVersion(String quarkusVersion) {
        this.quarkusVersion = quarkusVersion;
    }

    public String getCamelSpringBootBomGroupId() {
        return camelSpringBootBomGroupId;
    }

    public void setCamelSpringBootBomGroupId(String camelSpringBootBomGroupId) {
        this.camelSpringBootBomGroupId = camelSpringBootBomGroupId;
    }

    public String getCamelSpringBootBomArtifactId() {
        return camelSpringBootBomArtifactId;
    }

    public void setCamelSpringBootBomArtifactId(String camelSpringBootBomArtifactId) {
        this.camelSpringBootBomArtifactId = camelSpringBootBomArtifactId;
    }

    public String getQuarkusBomGroupId() {
        return quarkusBomGroupId;
    }

    public void setQuarkusBomGroupId(String quarkusBomGroupId) {
        this.quarkusBomGroupId = quarkusBomGroupId;
    }

    public String getQuarkusBomArtifactId() {
        return quarkusBomArtifactId;
    }

    public void setQuarkusBomArtifactId(String quarkusBomArtifactId) {
        this.quarkusBomArtifactId = quarkusBomArtifactId;
    }

    public String getCamelQuarkusBomGroupId() {
        return camelQuarkusBomGroupId;
    }

    public void setCamelQuarkusBomGroupId(String camelQuarkusBomGroupId) {
        this.camelQuarkusBomGroupId = camelQuarkusBomGroupId;
    }

    public String getCamelQuarkusBomArtifactId() {
        return camelQuarkusBomArtifactId;
    }

    public void setCamelQuarkusBomArtifactId(String camelQuarkusBomArtifactId) {
        this.camelQuarkusBomArtifactId = camelQuarkusBomArtifactId;
    }

    public Map<String, Object> toMap() {
        JsonObject jo = new JsonObject();
        jo.put("runtime", runtime);
        jo.put("camelVersion", camelVersion);
        if (camelSpringBootVersion != null) {
            jo.put("camelSpringBootVersion", camelSpringBootVersion);
        }
        if (camelQuarkusVersion != null) {
            jo.put("camelQuarkusVersion", camelQuarkusVersion);
        }
        if (springBootVersion != null) {
            jo.put("springBootVersion", springBootVersion);
        }
        if (quarkusVersion != null) {
            jo.put("quarkusVersion", quarkusVersion);
        }
        if (camelSpringBootBomGroupId != null) {
            jo.put("camelSpringBootBomGroupId", camelSpringBootBomGroupId);
        }
        if (camelSpringBootBomArtifactId != null) {
            jo.put("camelSpringBootBomArtifactId", camelSpringBootBomArtifactId);
        }
        if (quarkusBomGroupId != null) {
            jo.put("quarkusBomGroupId", quarkusBomGroupId);
        }
        if (quarkusBomArtifactId != null) {
            jo.put("quarkusBomArtifactId", quarkusBomArtifactId);
        }
        if (camelQuarkusBomGroupId != null) {
            jo.put("camelQuarkusBomGroupId", camelQuarkusBomGroupId);
        }
        if (camelQuarkusBomArtifactId != null) {
            jo.put("camelQuarkusBomArtifactId", camelQuarkusBomArtifactId);
        }
        return jo;
    }
}
