/**
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
package org.apache.camel.maven.packaging.model;

import java.util.ArrayList;
import java.util.List;

import static org.apache.camel.maven.packaging.StringHelper.cutLastZeroDigit;

public class DataFormatModel {

    private final boolean coreOnly;

    private String kind;
    private String name;
    private String modelName;
    private String title;
    private String description;
    private String firstVersion;
    private String label;
    private String deprecated;
    private String deprecationNote;
    private String javaType;
    private String groupId;
    private String artifactId;
    private String version;
    private final List<DataFormatOptionModel> dataFormatOptions = new ArrayList<DataFormatOptionModel>();

    public DataFormatModel() {
        this(false);
    }

    public DataFormatModel(boolean coreOnly) {
        this.coreOnly = coreOnly;
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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
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

    public List<DataFormatOptionModel> getDataFormatOptions() {
        return dataFormatOptions;
    }

    public void addDataFormatOption(DataFormatOptionModel option) {
        dataFormatOptions.add(option);
    }

    public String getShortJavaType() {
        if (javaType.startsWith("java.util.Map")) {
            return "Map";
        } else if (javaType.startsWith("java.util.Set")) {
            return "Set";
        } else if (javaType.startsWith("java.util.List")) {
            return "List";
        }
        int pos = javaType.lastIndexOf(".");
        if (pos != -1) {
            return javaType.substring(pos + 1);
        } else {
            return javaType;
        }
    }

    public String getDocLink() {
        if ("camel-core".equals(artifactId)) {
            return coreOnly ? "src/main/docs" : "../camel-core/src/main/docs";
        } else {
            return artifactId + "/src/main/docs";
        }
    }

    public String getFirstVersionShort() {
        return cutLastZeroDigit(firstVersion);
    }

}
