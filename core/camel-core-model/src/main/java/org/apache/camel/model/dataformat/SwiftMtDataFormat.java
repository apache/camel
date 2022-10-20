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

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Encode and decode SWIFT MT messages.
 */
@Metadata(firstVersion = "3.20.0", label = "dataformat,transformation,swift", title = "SWIFT MT")
@XmlRootElement(name = "swiftMt")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwiftMtDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String writeInJson;

    public SwiftMtDataFormat() {
        super("swiftMt");
    }

    public SwiftMtDataFormat(String writeInJson) {
        this();
        this.writeInJson = writeInJson;
    }

    public String getWriteInJson() {
        return writeInJson;
    }

    /**
     * The flag indicating that messages must be marshalled in a JSON format.
     *
     * @param writeInJson {@code true} if messages must be marshalled in a JSON format, {@code false} otherwise.
     */
    public void setWriteInJson(String writeInJson) {
        this.writeInJson = writeInJson;
    }

}
