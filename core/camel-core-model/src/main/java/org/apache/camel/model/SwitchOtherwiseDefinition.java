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

import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/** The fixed fallback destination of a switch. */
@Metadata(label = "configuration", description = "The fixed endpoint used when no switch case matches")
@XmlRootElement(name = "switchOtherwise")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwitchOtherwiseDefinition implements EndpointRequiredDefinition {
    @XmlAttribute(required = true)
    @Metadata(description = "The fixed destination URI. Supports property placeholders, but not Simple expressions.")
    private String uri;
    @XmlTransient
    private final ToDefinition toDefinition = new ToDefinition();

    public SwitchOtherwiseDefinition copyDefinition() {
        SwitchOtherwiseDefinition copy = new SwitchOtherwiseDefinition();
        copy.setUri(uri);
        return copy;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
        toDefinition.setUri(uri);
    }

    /** The destination node, available for endpoint discovery before route startup. */
    @XmlTransient
    public ToDefinition getToDefinition() {
        return toDefinition;
    }

    // JAXB populates fields directly, so initialize the traversal node after loading.
    private void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
        toDefinition.setUri(uri);
        if (parent instanceof SwitchDefinition sw) {
            sw.setOtherwise(this);
        }
    }

    @Override
    @XmlTransient
    public String getEndpointUri() {
        return uri;
    }
}
