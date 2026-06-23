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
package org.apache.camel.model.transformer;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.model.CopyableDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Metadata;

/**
 * A transformer which declarative transforms message content according to the input and/or output type declared on the
 * route level.
 *
 * If you specify from='java:com.example.ABC' and to='xml:XYZ', the transformer will be picked up when current message
 * type is 'java:com.example.ABC' and expected message type is 'xml:XYZ'. If you specify from='java' to='xml', then it
 * will be picked up for all of Java to xml transformation.
 *
 * Also, it's possible to specify a transformer name that identifies the transformer. Usually the name is a combination
 * of a scheme and a name that represents the supported data type name. The declared input and/or output can then
 * reference the transformer by its name.
 *
 * In case the transformer name should represent a data type scheme such as name='xml' that specific transformer will
 * also be picked up for all of Java to xml and xml to Java transformation as a fallback when no matching transformer is
 * found.
 */
@Metadata(label = "transformation")
@XmlType(name = "transformer")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class TransformerDefinition implements CopyableDefinition<TransformerDefinition> {

    @XmlAttribute
    @Metadata(description = "Scheme name supported by the transformer."
                            + " If specified, the transformer will be picked up for all from/to transformation of this scheme."
                            + " Scheme matching is performed only when no exactly matched transformer exists.")
    private String scheme;
    @XmlAttribute
    @Metadata(description = "Name under which the transformer gets referenced when specifying input/output data type on routes."
                            + " If the name matches a data type scheme, the transformer will be picked up as a fallback.")
    private String name;
    @XmlAttribute
    @Metadata(description = "The source (from) data type name. If you specify 'xml:XYZ', the transformer is picked up"
                            + " when source type matches. If you specify just 'xml', it matches all xml source types.")
    private String fromType;
    @XmlAttribute
    @Metadata(description = "The destination (to) data type name. If you specify 'json:XYZ', the transformer is picked up"
                            + " when destination type matches. If you specify just 'json', it matches all json destination types.")
    private String toType;

    protected TransformerDefinition() {
    }

    protected TransformerDefinition(TransformerDefinition source) {
        this.scheme = source.scheme;
        this.name = source.name;
        this.fromType = source.fromType;
        this.toType = source.toType;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFromType() {
        return fromType;
    }

    public void setFromType(String from) {
        this.fromType = from;
    }

    public void setFromType(Class<?> clazz) {
        this.fromType = new DataType(clazz).toString();
    }

    public String getToType() {
        return toType;
    }

    public void setToType(String to) {
        this.toType = to;
    }

    public void setToType(Class<?> clazz) {
        this.toType = new DataType(clazz).toString();
    }

}
