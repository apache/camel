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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Rest security basic auth definition
 */
@Metadata(label = "rest")
@XmlRootElement(name = "apiKey")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestSecurityApiKey extends RestSecurityDefinition {

    @XmlAttribute(name = "name", required = true)
    @Metadata(required = true)
    private String name;

    @XmlAttribute(name = "inHeader")
    @Metadata(javaType = "java.lang.Boolean")
    private String inHeader;

    @XmlAttribute(name = "inQuery")
    @Metadata(javaType = "java.lang.Boolean")
    private String inQuery;

    public RestSecurityApiKey() {
    }

    public RestSecurityApiKey(RestDefinition rest) {
        super(rest);
    }

    public String getName() {
        return name;
    }

    /**
     * The name of the header or query parameter to be used.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getInHeader() {
        return inHeader;
    }

    /**
     * To use header as the location of the API key.
     */
    public void setInHeader(String inHeader) {
        this.inHeader = inHeader;
    }

    public String getInQuery() {
        return inQuery;
    }

    /**
     * To use query parameter as the location of the API key.
     */
    public void setInQuery(String inQuery) {
        this.inQuery = inQuery;
    }

    public RestSecurityApiKey withHeader(String name) {
        setName(name);
        setInHeader(Boolean.toString(true));
        setInQuery(Boolean.toString(false));
        return this;
    }

    public RestSecurityApiKey withQuery(String name) {
        setName(name);
        setInQuery(Boolean.toString(true));
        setInHeader(Boolean.toString(false));
        return this;
    }

    public RestSecuritiesDefinition end() {
        return rest.getSecurityDefinitions();
    }
}
