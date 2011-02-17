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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.spi.NodeIdFactory;

/**
 * Allows an element to have an optional ID specified
 *
 * @version 
 */
@XmlType(name = "optionalIdentifiedDefinition")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class OptionalIdentifiedDefinition<T extends OptionalIdentifiedDefinition<T>> {
    @XmlAttribute(required = false)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    private String id;
    @XmlTransient
    private boolean customId;
    @XmlElement(required = false)
    private DescriptionDefinition description;

    /**
     * Gets the value of the id property.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     */
    public void setId(String value) {
        this.id = value;
        customId = true;
    }

    public DescriptionDefinition getDescription() {
        return description;
    }

    public void setDescription(DescriptionDefinition description) {
        this.description = description;
    }

    /**
     * Returns a short name for this node which can be useful for ID generation or referring to related resources like images
     *
     * @return defaults to "node" but derived nodes should overload this to provide a unique name
     */
    public String getShortName() {
        return "node";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the description of this node
     *
     * @param id  sets the id, use null to not set an id
     * @param text  sets the text description, use null to not set a text
     * @param lang  sets the language for the description, use null to not set a language
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public T description(String id, String text, String lang) {
        if (id != null) {
            setId(id);
        }
        if (text != null) {
            if (description == null) {
                description = new DescriptionDefinition();
            }
            description.setText(text);
        }
        if (lang != null) {
            if (description == null) {
                description = new DescriptionDefinition();
            }
            description.setLang(lang);
        }
        return (T) this;
    }

    /**
     * Sets the id of this node
     *
     * @param id  the id
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public T id(String id) {
        setId(id);
        return (T) this;
    }

    /**
     * Gets the node id, creating one if not already set.
     */
    public String idOrCreate(NodeIdFactory factory) {
        if (id == null) {
            id = factory.createId(this);
        }
        return getId();
    }

    /**
     * Returns whether a custom id has been assigned
     */
    public boolean hasCustomIdAssigned() {
        return customId;
    }

    /**
     * Returns the description text or null if there is no description text associated with this node
     */
    public String getDescriptionText() {
        return (description != null) ? description.getText() : null;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

}