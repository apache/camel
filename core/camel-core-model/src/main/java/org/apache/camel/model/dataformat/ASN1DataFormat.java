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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Encode and decode data structures using Abstract Syntax Notation One (ASN.1).
 */
@Metadata(firstVersion = "2.20.0", label = "dataformat,transformation,file", title = "ASN.1 File")
@XmlRootElement(name = "asn1")
@XmlAccessorType(XmlAccessType.FIELD)
public class ASN1DataFormat extends DataFormatDefinition {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String usingIterator;
    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;
    @XmlTransient
    private Class<?> unmarshalType;

    public ASN1DataFormat() {
        super("asn1");
    }

    public ASN1DataFormat(Boolean usingIterator) {
        this();
        setUsingIterator(usingIterator != null ? usingIterator.toString() : null);
    }

    public ASN1DataFormat(String unmarshalTypeName) {
        this();
        setUsingIterator(Boolean.toString(true));
        setUnmarshalTypeName(unmarshalTypeName);
    }

    public ASN1DataFormat(Class<?> unmarshalType) {
        setUsingIterator(Boolean.toString(true));
        this.unmarshalType = unmarshalType;
    }

    public String getUsingIterator() {
        return usingIterator;
    }

    /**
     * If the asn1 file has more then one entry, the setting this option to true, allows to work with the splitter EIP,
     * to split the data using an iterator in a streaming mode.
     */
    public void setUsingIterator(String usingIterator) {
        this.usingIterator = usingIterator;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }
}
