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
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/** One literal value and one fixed destination. */
@Metadata(label = "configuration", description = "A literal switch case with one fixed endpoint destination")
@XmlRootElement(name = "case")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwitchCaseDefinition extends OptionalIdentifiedDefinition<SwitchCaseDefinition>
        implements EndpointRequiredDefinition {
    @XmlAttribute(required = true)
    @Metadata(description = "The case-insensitive literal value for a scalar selector.")
    private String value;
    @XmlAttribute(required = true)
    @Metadata(description = "The fixed destination URI. Supports property placeholders, but not Simple expressions.")
    private String uri;
    @XmlTransient
    private SwitchDefinition parent;
    @XmlTransient
    private ToDefinition toDefinition;

    public SwitchCaseDefinition() {
    }

    public SwitchCaseDefinition(String value, String uri) {
        this.value = value;
        this.uri = uri;
    }

    protected SwitchCaseDefinition(SwitchCaseDefinition source) {
        super(source);
        this.value = source.value;
        this.uri = source.uri;
    }

    public SwitchCaseDefinition copyDefinition() {
        return new SwitchCaseDefinition(this);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    /** The destination node shared by reification and management instrumentation. */
    @XmlTransient
    public ToDefinition getToDefinition() {
        if (toDefinition == null) {
            toDefinition = new ToDefinition(uri);
        }
        toDefinition.setParent(parent);
        if (hasCustomIdAssigned()) {
            toDefinition.setId(getId());
        } else {
            toDefinition.setGeneratedId(getId());
        }
        toDefinition.setDescription(getDescription());
        toDefinition.setNote(getNote());
        toDefinition.setLocation(getLocation());
        toDefinition.setLineNumber(getLineNumber());
        return toDefinition;
    }

    @Override
    public String getEndpointUri() {
        return uri;
    }

    @Override
    public SwitchDefinition getParent() {
        return parent;
    }

    public void setParent(SwitchDefinition parent) {
        this.parent = parent;
    }

    @Override
    public String getShortName() {
        return "case";
    }

    @Override
    public String getLabel() {
        return "case[" + value + "]";
    }

    @Override
    public String toString() {
        return getLabel() + " -> " + uri;
    }
}
