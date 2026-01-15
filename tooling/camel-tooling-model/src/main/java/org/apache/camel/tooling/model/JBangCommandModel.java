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

import java.util.ArrayList;
import java.util.List;

/**
 * Model for Camel JBang CLI commands.
 */
public class JBangCommandModel {

    protected final List<JBangCommand> commands = new ArrayList<>();

    public List<JBangCommand> getCommands() {
        return commands;
    }

    public void addCommand(JBangCommand command) {
        commands.add(command);
    }

    /**
     * Represents a JBang CLI command.
     */
    public static class JBangCommand {

        private String name;
        private String fullName;
        private String description;
        private boolean deprecated;
        private String deprecationNote;
        private String sourceClass;
        private final List<JBangCommandOption> options = new ArrayList<>();
        private final List<JBangCommand> subcommands = new ArrayList<>();

        public JBangCommand() {
        }

        public JBangCommand(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
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

        public String getSourceClass() {
            return sourceClass;
        }

        public void setSourceClass(String sourceClass) {
            this.sourceClass = sourceClass;
        }

        public List<JBangCommandOption> getOptions() {
            return options;
        }

        public void addOption(JBangCommandOption option) {
            options.add(option);
        }

        public List<JBangCommand> getSubcommands() {
            return subcommands;
        }

        public void addSubcommand(JBangCommand subcommand) {
            subcommands.add(subcommand);
        }

        public boolean hasSubcommands() {
            return !subcommands.isEmpty();
        }

        public boolean hasOptions() {
            return !options.isEmpty();
        }

        /**
         * Gets the file name (without path) for this command's documentation page.
         */
        public String getDocFileName() {
            String fn = fullName != null ? fullName : name;
            return "camel-jbang-" + fn.replace(" ", "-") + ".adoc";
        }

        /**
         * Gets the relative path (including subfolder) for this command's documentation page.
         */
        public String getDocFilePath() {
            return "jbang-commands/" + getDocFileName();
        }

        /**
         * Gets the xref path for linking to this command's documentation page.
         */
        public String getDocXref() {
            return "jbang-commands/" + getDocFileName();
        }
    }

    /**
     * Represents an option for a JBang CLI command.
     */
    public static class JBangCommandOption {

        private String names;
        private String description;
        private Object defaultValue;
        private String javaType;
        private String type;
        private boolean required;
        private boolean deprecated;
        private String deprecationNote;
        private boolean hidden;
        private List<String> enums;
        private String paramLabel;

        public JBangCommandOption() {
        }

        public String getNames() {
            return names;
        }

        public void setNames(String names) {
            this.names = names;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
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

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
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

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public List<String> getEnums() {
            return enums;
        }

        public void setEnums(List<String> enums) {
            this.enums = enums;
        }

        public String getParamLabel() {
            return paramLabel;
        }

        public void setParamLabel(String paramLabel) {
            this.paramLabel = paramLabel;
        }

        public String getShortJavaType() {
            return Strings.getClassShortName(javaType);
        }

        public String getShortDefaultValue() {
            return defaultValue != null ? defaultValue.toString() : "";
        }
    }
}
