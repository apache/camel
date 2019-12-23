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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Models a series of string key/value pairs for configuring some global options
 * on a Camel context such as max debug log length.
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "globalOptions")
@XmlAccessorType(XmlAccessType.FIELD)
public class GlobalOptionsDefinition {
    @XmlElement(name = "globalOption")
    private List<GlobalOptionDefinition> globalOptions;

    public GlobalOptionsDefinition() {
    }

    /**
     * A series of global options as key value pairs
     */
    public void setGlobalOptions(List<GlobalOptionDefinition> globalOptions) {
        this.globalOptions = globalOptions;
    }

    public List<GlobalOptionDefinition> getGlobalOptions() {
        return globalOptions;
    }

    public Map<String, String> asMap() {
        return getGlobalOptions().stream().collect(Collectors.toMap(o -> o.getKey(), o -> o.getValue(), (o1, o2) -> o2));
    }

}
