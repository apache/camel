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

import java.util.List;

@SuppressWarnings("unused")
public abstract class BaseOptionModel {

    protected String name;

    protected String kind;
    protected String displayName;
    protected String group;
    protected String label;
    protected boolean required;
    protected String type;
    protected String javaType;
    protected List<String> enums;
    protected List<String> oneOfs;
    protected String prefix;
    protected String optionalPrefix;
    protected boolean multiValue;
    protected boolean deprecated;
    protected String deprecationNote;
    protected boolean secret;
    protected Object defaultValue;
    protected String defaultValueNote;
    protected boolean asPredicate;
    protected String configurationClass;
    protected String configurationField;
    protected String description;

    // todo: move this as a helper method
    protected boolean newGroup; // special for documentation rendering

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
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

    public List<String> getEnums() {
        return enums;
    }

    public void setEnums(List<String> enums) {
        this.enums = enums;
    }

    public List<String> getOneOfs() {
        return oneOfs;
    }

    public void setOneOfs(List<String> oneOfs) {
        this.oneOfs = oneOfs;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getOptionalPrefix() {
        return optionalPrefix;
    }

    public void setOptionalPrefix(String optionalPrefix) {
        this.optionalPrefix = optionalPrefix;
    }

    public boolean isMultiValue() {
        return multiValue;
    }

    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
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

    public boolean isSecret() {
        return secret;
    }

    public void setSecret(boolean secret) {
        this.secret = secret;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValueNote() {
        return defaultValueNote;
    }

    public void setDefaultValueNote(String defaultValueNote) {
        this.defaultValueNote = defaultValueNote;
    }

    public boolean isAsPredicate() {
        return asPredicate;
    }

    public void setAsPredicate(boolean asPredicate) {
        this.asPredicate = asPredicate;
    }

    public String getConfigurationClass() {
        return configurationClass;
    }

    public void setConfigurationClass(String configurationClass) {
        this.configurationClass = configurationClass;
    }

    public String getConfigurationField() {
        return configurationField;
    }

    public void setConfigurationField(String configurationField) {
        this.configurationField = configurationField;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isNewGroup() {
        return newGroup;
    }

    public void setNewGroup(boolean newGroup) {
        this.newGroup = newGroup;
    }

    public String getShortGroup() {
        if (group.endsWith(" (advanced)")) {
            return group.substring(0, group.length() - 11);
        }
        return group;
    }

    public String getShortJavaType() {
        return Strings.getClassShortName(javaType);
    }

    @Deprecated
    public String getShortJavaType(int watermark) {
        String text = Strings.getClassShortName(type);
        // if its some kind of java object then lets wrap it as its long
        if ("object".equals(type)) {
            text = Strings.wrapCamelCaseWords(text, watermark, " ");
        }
        return text;
    }

    public String getShortDefaultValue(int watermark) {
        String text = defaultValue != null ? defaultValue.toString() : "";
        if (text.endsWith("<T>")) {
            text = text.substring(0, text.length() - 3);
        } else if (text.endsWith("<T>>")) {
            text = text.substring(0, text.length() - 4);
        }
        return text;
    }

    public String getShortName(int watermark) {
        String text = Strings.wrapCamelCaseWords(name, watermark, " ");
        // ensure the option name starts with lower-case
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

}
