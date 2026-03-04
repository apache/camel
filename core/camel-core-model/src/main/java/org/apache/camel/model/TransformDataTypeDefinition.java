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

import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Metadata;

/**
 * Transforms the message body based on known data type transformers.
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "transformDataType")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransformDataTypeDefinition extends NoOutputDefinition<TransformDataTypeDefinition> {

    @XmlAttribute
    private String fromType;
    @XmlAttribute(required = true)
    private String toType;

    public TransformDataTypeDefinition() {
    }

    protected TransformDataTypeDefinition(TransformDataTypeDefinition source) {
        super(source);
        this.fromType = source.fromType;
        this.toType = source.toType;
    }

    public TransformDataTypeDefinition(DataType fromType, DataType toType) {
        this.fromType = fromType != null ? fromType.getFullName() : null;
        this.toType = toType.getFullName();
    }

    @Override
    public TransformDataTypeDefinition copyDefinition() {
        return new TransformDataTypeDefinition(this);
    }

    @Override
    public String toString() {
        if (fromType != null) {
            return "TransformDataType[" + fromType + ", " + toType + "]";
        } else {
            return "TransformDataType[" + toType + "]";
        }
    }

    @Override
    public String getShortName() {
        return "transformDataType";
    }

    @Override
    public String getLabel() {
        if (fromType != null) {
            return "transformDataType[" + fromType + ", " + toType + "]";
        } else {
            return "transformDataType[" + toType + "]";
        }
    }

    /**
     * From type used in data type transformation.
     */
    public void setFromType(String fromType) {
        this.fromType = fromType;
    }

    public String getFromType() {
        return fromType;
    }

    /**
     * To type used as a target data type in the transformation.
     */
    public void setToType(String toType) {
        this.toType = toType;
    }

    public String getToType() {
        return toType;
    }
}
