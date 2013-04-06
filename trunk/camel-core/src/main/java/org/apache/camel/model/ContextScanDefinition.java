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

/**
 * <code>ContextScanDefinition</code> represents a &lt;contextScan/&gt element.
 */
@XmlRootElement(name = "contextScan")
@XmlAccessorType(XmlAccessType.FIELD)
public class ContextScanDefinition {
    @XmlElement(name = "excludes")
    private List<String> excludes = new ArrayList<String>();
    @XmlElement(name = "includes")
    private List<String> includes = new ArrayList<String>();

    public ContextScanDefinition() {
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    protected void clear() {
        excludes.clear();
        includes.clear();
    }
}