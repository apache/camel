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
 * Removes a named header from the message
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "removeHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemoveHeaderDefinition extends NoOutputDefinition<RemoveHeaderDefinition> {

    @XmlAttribute(required = true)
    private String name;

    public RemoveHeaderDefinition() {
    }

    protected RemoveHeaderDefinition(RemoveHeaderDefinition source) {
        super(source);
        this.name = source.name;
    }

    public RemoveHeaderDefinition(String headerName) {
        setName(headerName);
    }

    @Override
    public RemoveHeaderDefinition copyDefinition() {
        return new RemoveHeaderDefinition(this);
    }

    @Override
    public String toString() {
        return "RemoveHeader[" + getName() + "]";
    }

    @Override
    public String getShortName() {
        return "removeHeader";
    }

    @Override
    public String getLabel() {
        return "removeHeader[" + getName() + "]";
    }

    public String getName() {
        return name;
    }

    /**
     * Name of header to remove
     */
    public void setName(String name) {
        this.name = name;
    }

}
