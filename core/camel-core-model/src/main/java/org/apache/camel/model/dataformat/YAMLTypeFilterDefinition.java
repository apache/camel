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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

@Metadata(label = "dataformat,transformation,yaml", title = "YAML Type Filter")
@XmlRootElement(name = "typeFilter")
@XmlAccessorType(XmlAccessType.FIELD)
public final class YAMLTypeFilterDefinition {
    @XmlAttribute
    private String value;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.model.dataformat.YAMLTypeFilterType")
    private String type;

    public String getValue() {
        return value;
    }

    /**
     * Value of type such as class name or regular expression
     */
    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    /**
     * Whether to filter by class type or regular expression
     */
    public void setType(String type) {
        this.type = type;
    }
}
