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

public class DataFormatModel extends ArtifactModel<DataFormatOptionModel> {

    private final boolean coreOnly;

    private String kind;
    private String modelName;
//    private final List<DataFormatOptionModel> dataFormatOptions = new ArrayList<>();

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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setDeprecated(String deprecated) {
        setDeprecated("true".equals(deprecated));
    }

//    public List<DataFormatOptionModel> getDataFormatOptions() {
//        return dataFormatOptions;
//    }
//
//    public void addDataFormatOption(DataFormatOptionModel option) {
//        dataFormatOptions.add(option);
//    }
//
    public String getDocLink() {
        // special for these components
        if ("camel-fhir".equals(artifactId)) {
            return "camel-fhir/camel-fhir-component/src/main/docs";
        }

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
