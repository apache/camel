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
package org.apache.camel.model.rest;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Rest security bearer token authentication definition
 */
@Metadata(label = "rest,security,configuration")
@XmlRootElement(name = "bearerToken")
@XmlAccessorType(XmlAccessType.FIELD)
public class BearerTokenDefinition extends RestSecurityDefinition {

    @XmlAttribute
    private String format;

    @SuppressWarnings("unused")
    public BearerTokenDefinition() {
    }

    public BearerTokenDefinition(RestDefinition rest) {
        super(rest);
    }

    public String getFormat() {
        return format;
    }

    /**
     * A hint to the client to identify how the bearer token is formatted.
     */
    public void setFormat(String format) {
        this.format = format;
    }
}
