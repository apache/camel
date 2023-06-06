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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseModel<O extends BaseOptionModel> {

    protected String name;
    protected String title;
    protected String description;
    protected String firstVersion;
    protected String javaType;
    protected String label;
    protected boolean deprecated;
    protected String deprecatedSince;
    protected String deprecationNote;
    protected final List<O> options = new ArrayList<>();
    protected SupportLevel supportLevel;
    protected boolean nativeSupported;
    protected Map<String, Object> metadata = new LinkedHashMap<>();

    public static Comparator<BaseModel<?>> compareTitle() {
        return (m1, m2) -> m1.getTitle().compareToIgnoreCase(m2.getTitle());
    }

    public abstract String getKind();

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

    public String getDeprecatedSince() {
        return deprecatedSince;
    }

    public void setDeprecatedSince(String deprecatedSince) {
        this.deprecatedSince = deprecatedSince;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public List<O> getOptions() {
        return options;
    }

    public void addOption(O option) {
        options.add(option);
    }

    public String getShortJavaType() {
        return Strings.getClassShortName(javaType);
    }

    public String getFirstVersionShort() {
        return !Strings.isNullOrEmpty(firstVersion) ? Strings.cutLastZeroDigit(firstVersion) : "";
    }

    public SupportLevel getSupportLevel() {
        return supportLevel;
    }

    public void setSupportLevel(SupportLevel supportLevel) {
        this.supportLevel = supportLevel;
    }

    /**
     * True if the part represented by this model supports compilation to native code.
     */
    public boolean isNativeSupported() {
        return nativeSupported;
    }

    public void setNativeSupported(boolean nativeSupported) {
        this.nativeSupported = nativeSupported;
    }

    /**
     * A free form map of key value pair representing this {@link BaseModel}'s metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return name;
    }
}
