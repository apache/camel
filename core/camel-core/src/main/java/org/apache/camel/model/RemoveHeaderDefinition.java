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
 * Removes a named header from the message
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "removeHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemoveHeaderDefinition extends NoOutputDefinition<RemoveHeaderDefinition> {
    @XmlAttribute(required = true)
    private String headerName;

    public RemoveHeaderDefinition() {
    }

    public RemoveHeaderDefinition(String headerName) {
        setHeaderName(headerName);
    }

    @Override
    public String toString() {
        return "RemoveHeader[" + getHeaderName() + "]";
    }

    @Override
    public String getShortName() {
        return "removeHeader";
    }

    @Override
    public String getLabel() {
        return "removeHeader[" + getHeaderName() + "]";
    }

    /**
     * Name of header to remove
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderName() {
        return headerName;
    }
}
