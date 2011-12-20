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
package org.apache.camel.core.xml.util.jsse;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents a set of regular expression based filter patterns for
 * including and excluding content of some type.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "filterParameters", propOrder = {"include", "exclude"})
public class FilterParametersDefinition {

    protected List<String> include;
    protected List<String> exclude;

    /**
     * Returns a live copy of the list of patterns to include.
     * The list of excludes takes precedence over the include patterns.
     *
     * @return the list of patterns to include
     */
    public List<String> getInclude() {
        if (this.include == null) {
            this.include = new ArrayList<String>();
        }
        return this.include;
    }

    /**
     * Returns a live copy of the list of patterns to exclude.
     * This list takes precedence over the include patterns.
     *
     * @return the list of patterns to exclude
     */
    public List<String> getExclude() {
        if (exclude == null) {
            exclude = new ArrayList<String>();
        }
        return this.exclude;
    }
}
