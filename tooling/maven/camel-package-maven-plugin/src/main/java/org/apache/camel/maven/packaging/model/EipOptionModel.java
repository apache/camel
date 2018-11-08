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

import org.apache.camel.maven.packaging.StringHelper;

import static org.apache.camel.maven.packaging.StringHelper.wrapCamelCaseWords;

public class EipOptionModel {

    private String name;
    private String displayName;
    private String title;
    private String required;
    private String javaType;
    private String type;
    private String label;
    private String defaultValue;
    private String description;
    private boolean deprecated;
    private String deprecationNote;
    private boolean input;
    private boolean output;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequired() {
        return required;
    }

    public void setRequired(String required) {
        this.required = required;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
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

    public String getShortJavaType() {
        // TODO: use watermark in the others
        return getShortJavaType(40);
    }

    public String getShortJavaType(int watermark) {

        String text = StringHelper.getClassShortName(javaType);

        // if its some kind of java object then lets wrap it as its long
        if ("object".equals(type)) {
            text = wrapCamelCaseWords(text, watermark, " ");
        }
        return text;
    }

    public String getShortDefaultValue(int watermark) {
        if (defaultValue.isEmpty()) {
            return "";
        }
        String text = defaultValue;
        if (text.endsWith("<T>")) {
            text = text.substring(0, text.length() - 3);
        } else if (text.endsWith("<T>>")) {
            text = text.substring(0, text.length() - 4);
        }

        // TODO: dirty hack for AUTO_ACKNOWLEDGE which we should wrap
        if ("AUTO_ACKNOWLEDGE".equals(text)) {
            return "AUTO_ ACKNOWLEDGE";
        }

        return text;
    }

    public String getShortName(int watermark) {
        String text = wrapCamelCaseWords(name, watermark, " ");
        // ensure the option name starts with lower-case
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

}

