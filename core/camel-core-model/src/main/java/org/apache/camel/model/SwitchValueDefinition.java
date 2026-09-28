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

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/** A named, typed literal in a switch case. */
@Metadata(label = "configuration", description = "A named literal in a composite switch case")
@XmlRootElement(name = "switchValue")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwitchValueDefinition {
    @XmlAttribute(required = true)
    @Metadata(description = "The exact name of the selected field.")
    private String name;
    @XmlAttribute(required = true)
    @Metadata(description = "The literal value, never an expression.")
    private String value;
    @XmlAttribute
    @Metadata(defaultValue = "string", enums = "string,boolean,number", description = "The literal type.")
    private String type;

    public SwitchValueDefinition() {
    }

    public SwitchValueDefinition(String name, Object value) {
        this.name = name;
        if (value instanceof String text) {
            this.value = text;
            this.type = "string";
        } else if (value instanceof Boolean) {
            this.value = value.toString();
            this.type = "boolean";
        } else if (value instanceof Number) {
            this.value = value.toString();
            this.type = "number";
        } else {
            throw new IllegalArgumentException("Switch values must be strings, booleans or numbers");
        }
    }

    public SwitchValueDefinition copyDefinition() {
        SwitchValueDefinition copy = new SwitchValueDefinition();
        copy.name = name;
        copy.value = value;
        copy.type = type;
        return copy;
    }

    public Object asLiteral() {
        if (value == null) {
            throw new IllegalArgumentException("Switch value is required for key: " + name);
        }
        return switch (type == null ? "string" : type) {
            case "string" -> value;
            case "boolean" -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IllegalArgumentException("Switch boolean must be true or false for key: " + name);
                }
                yield Boolean.valueOf(value);
            }
            case "number" -> new BigDecimal(value);
            default -> throw new IllegalArgumentException("Unsupported switch literal type: " + type);
        };
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
