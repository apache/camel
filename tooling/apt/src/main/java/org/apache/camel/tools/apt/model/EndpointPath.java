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
package org.apache.camel.tools.apt.model;

import java.util.Set;

import org.apache.camel.tools.apt.helper.CollectionStringBuffer;

public final class EndpointPath {

    private String name;
    private String displayName;
    private String type;
    private String required;
    private String defaultValue;
    private String documentation;
    private boolean deprecated;
    private String deprecationNote;
    private boolean secret;
    private String group;
    private String label;
    private boolean enumType;
    private Set<String> enums;

    public EndpointPath(String name, String displayName, String type, String required, String defaultValue, String documentation,
                        boolean deprecated, String deprecationNote, boolean secret, String group, String label,
                        boolean enumType, Set<String> enums) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.documentation = documentation;
        this.deprecated = deprecated;
        this.deprecationNote = deprecationNote;
        this.secret = secret;
        this.group = group;
        this.label = label;
        this.enumType = enumType;
        this.enums = enums;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getType() {
        return type;
    }

    public String getRequired() {
        return required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDocumentation() {
        return documentation;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public String getDeprecationNote() {
        return deprecationNote;
    }

    public boolean isSecret() {
        return secret;
    }

    public boolean isEnumType() {
        return enumType;
    }

    public Set<String> getEnums() {
        return enums;
    }

    public String getGroup() {
        return group;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EndpointPath that = (EndpointPath) o;

        if (!name.equals(that.name)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
