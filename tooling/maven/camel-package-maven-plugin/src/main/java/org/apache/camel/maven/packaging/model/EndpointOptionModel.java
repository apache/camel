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

import static org.apache.camel.maven.packaging.StringHelper.wrapCamelCaseWords;

public class EndpointOptionModel {

    private String name;
    private String displayName;
    private String kind;
    private String group;
    private String required;
    private String type;
    private String javaType;
    private String enums;
    private String prefix;
    private String multiValue;
    private String deprecated;
    private String deprecationNote;
    private String secret;
    private String defaultValue;
    private String description;
    private String enumValues;

    // special for documentation rendering
    private boolean newGroup;

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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getRequired() {
        return required;
    }

    public void setRequired(String required) {
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getEnums() {
        return enums;
    }

    public void setEnums(String enums) {
        this.enums = enums;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMultiValue() {
        return multiValue;
    }

    public void setMultiValue(String multiValue) {
        this.multiValue = multiValue;
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

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
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

    public String getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(String enumValues) {
        this.enumValues = enumValues;
    }

    public boolean isNewGroup() {
        return newGroup;
    }

    public void setNewGroup(boolean newGroup) {
        this.newGroup = newGroup;
    }

    public String getShortJavaType() {
        // TODO: use watermark in the others
        return getShortJavaType(40);
    }

    public String getShortJavaType(int watermark) {
        if (javaType.startsWith("java.util.Map")) {
            return "Map";
        } else if (javaType.startsWith("java.util.Set")) {
            return "Set";
        } else if (javaType.startsWith("java.util.List")) {
            return "List";
        }

        String text = javaType;

        int pos = text.lastIndexOf(".");
        if (pos != -1) {
            text = text.substring(pos + 1);
        }

        // if its some kind of java object then lets wrap it as its long
        if ("object".equals(type)) {
            text = wrapCamelCaseWords(text, watermark, " ");
        }
        return text;
    }

    public String getShortGroup() {
        if (group.endsWith(" (advanced)")) {
            return group.substring(0, group.length() - 11);
        }
        return group;
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
