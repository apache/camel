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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Scans for Java {@link org.apache.camel.builder.RouteBuilder} classes in java packages
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "packageScan")
@XmlAccessorType(XmlAccessType.FIELD)
public class PackageScanDefinition {
    @XmlElement(name = "package", required = true)
    private List<String> packages = new ArrayList<String>();
    @XmlElement
    private List<String> excludes = new ArrayList<String>();
    @XmlElement
    private List<String> includes = new ArrayList<String>();

    public PackageScanDefinition() {
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public List<String> getPackages() {
        return packages;
    }

    /**
     * Sets the java package names to use for scanning for route builder classes
     */
    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    /**
     * Exclude finding route builder from these java package names.
     */
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    /**
     * Include finding route builder from these java package names.
     */
    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    protected void clear() {
        packages.clear();
        excludes.clear();
        includes.clear();
    }
}
