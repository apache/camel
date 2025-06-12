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
package org.apache.camel.dsl.jbang.core.common;

import static org.apache.camel.dsl.jbang.core.common.CatalogLoader.QUARKUS_GROUP_ID;

public class RuntimeInformation {
    private RuntimeType type;
    private String camelVersion;
    private String camelQuarkusVersion;
    private String springBootVersion;

    private String quarkusVersion;
    private String quarkusGroupId;

    public RuntimeInformation() {
        this.quarkusGroupId = QUARKUS_GROUP_ID;
    }

    public RuntimeType getType() {
        return type;
    }

    public void setType(RuntimeType type) {
        this.type = type;
    }

    public String getCamelVersion() {
        return camelVersion;
    }

    public void setCamelVersion(String camelVersion) {
        this.camelVersion = camelVersion;
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

    public String getQuarkusGroupId() {
        return quarkusGroupId;
    }

    public void setQuarkusGroupId(String quarkusGroupId) {
        this.quarkusGroupId = quarkusGroupId;
    }
}
