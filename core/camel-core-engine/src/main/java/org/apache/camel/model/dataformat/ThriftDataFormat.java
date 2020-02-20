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
 * The Thrift data format is used for serialization and deserialization of
 * messages using Apache Thrift binary dataformat.
 */
@Metadata(firstVersion = "2.20.0", label = "dataformat,transformation", title = "Thrift")
@XmlRootElement(name = "thrift")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThriftDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String instanceClass;
    @XmlAttribute
    @Metadata(enums = "binary,json,sjson", defaultValue = "binary")
    private String contentTypeFormat;
    @XmlTransient
    private Object defaultInstance;

    public ThriftDataFormat() {
        super("thrift");
    }

    public ThriftDataFormat(String instanceClass) {
        this();
        setInstanceClass(instanceClass);
    }

    public ThriftDataFormat(String instanceClass, String contentTypeFormat) {
        this();
        setInstanceClass(instanceClass);
        setContentTypeFormat(contentTypeFormat);
    }

    public String getInstanceClass() {
        return instanceClass;
    }

    /**
     * Name of class to use when unmarshalling
     */
    public void setInstanceClass(String instanceClass) {
        this.instanceClass = instanceClass;
    }

    /**
     * Defines a content type format in which thrift message will be
     * serialized/deserialized from(to) the Java been. The format can either be
     * native or json for either native binary thrift, json or simple json
     * fields representation. The default value is binary.
     */
    public void setContentTypeFormat(String contentTypeFormat) {
        this.contentTypeFormat = contentTypeFormat;
    }

    public String getContentTypeFormat() {
        return contentTypeFormat;
    }

    public Object getDefaultInstance() {
        return defaultInstance;
    }

    public void setDefaultInstance(Object defaultInstance) {
        this.defaultInstance = defaultInstance;
    }

}
