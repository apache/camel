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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To call Kamelets
 */
@Metadata(label = "eip,routing,kamelet")
@XmlRootElement(name = "kamelet")
@XmlAccessorType(XmlAccessType.FIELD)
public class KameletDefinition extends OutputDefinition<KameletDefinition> {

    @XmlAttribute(required = true)
    private String name;

    public KameletDefinition() {
    }

    public KameletDefinition(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Kamelet[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "kamelet";
    }

    @Override
    public String getLabel() {
        return "kamelet";
    }

    public String getName() {
        return name;
    }

    /**
     * Name of the Kamelet (templateId/routeId) to call.
     *
     * Options for the kamelet can be specified using uri syntax, eg myname?count=4&type=gold.
     */
    public void setName(String name) {
        this.name = name;
    }
}
