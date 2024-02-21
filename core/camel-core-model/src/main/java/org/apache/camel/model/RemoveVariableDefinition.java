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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Removes a named variable
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "removeVariable")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemoveVariableDefinition extends NoOutputDefinition<RemoveVariableDefinition> {

    @XmlAttribute(required = true)
    private String name;

    public RemoveVariableDefinition() {
    }

    public RemoveVariableDefinition(String variableName) {
        this.name = variableName;
    }

    @Override
    public String toString() {
        return "RemoveVariable[" + name + "]";
    }

    @Override
    public String getShortName() {
        return "removeVariable";
    }

    @Override
    public String getLabel() {
        return "removeVariable[" + name + "]";
    }

    public String getName() {
        return name;
    }

    /**
     * Name of variable to remove.
     */
    public void setName(String name) {
        this.name = name;
    }
}
