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

public class EipModel {

    private String name;
    private String title;
    private String javaType;
    private String label;
    private String description;
    private boolean deprecated;
    private String deprecationNote;
    private boolean input;
    private boolean output;
    private final List<EipOptionModel> eipOptions = new ArrayList<EipOptionModel>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getDeprecationNote() {
        return deprecationNote;
    }

    public void setDeprecationNote(String deprecationNote) {
        this.deprecationNote = deprecationNote;
    }

    public boolean isInput() {
        return input;
    }

    public void setInput(boolean input) {
        this.input = input;
    }

    public String getInput() {
        return input ? "true" : "false";
    }

    public boolean isOutput() {
        return output;
    }

    public void setOutput(boolean output) {
        this.output = output;
    }

    public String getOutput() {
        return output ? "true" : "false";
    }

    public List<EipOptionModel> getEipOptions() {
        return eipOptions;
    }

    public void addEipOptionModel(EipOptionModel option) {
        eipOptions.add(option);
    }

    public String getDocLink() {
        // lets store EIP docs in a sub-folder as we have many EIPs
        return "src/main/docs/eips";
    }

}

