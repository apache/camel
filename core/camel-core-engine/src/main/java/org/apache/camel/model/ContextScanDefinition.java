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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Scans for Java {@link org.apache.camel.builder.RouteBuilder} instances in the
 * context {@link org.apache.camel.spi.Registry}.
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "contextScan")
@XmlAccessorType(XmlAccessType.FIELD)
public class ContextScanDefinition {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String includeNonSingletons;
    @XmlElement(name = "excludes")
    private List<String> excludes = new ArrayList<>();
    @XmlElement(name = "includes")
    private List<String> includes = new ArrayList<>();

    public ContextScanDefinition() {
    }

    public String getIncludeNonSingletons() {
        return includeNonSingletons;
    }

    /**
     * Whether to include non-singleton beans (prototypes)
     * <p/>
     * By default only singleton beans is included in the context scan
     */
    public void setIncludeNonSingletons(String includeNonSingletons) {
        this.includeNonSingletons = includeNonSingletons;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    /**
     * Exclude finding route builder from these java package names.
     */
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public List<String> getIncludes() {
        return includes;
    }

    /**
     * Include finding route builder from these java package names.
     */
    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    protected void clear() {
        excludes.clear();
        includes.clear();
    }
}
