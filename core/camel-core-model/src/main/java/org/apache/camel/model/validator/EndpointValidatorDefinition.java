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

package org.apache.camel.model.validator;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To use a Camel endpoint to perform validation on the route level.
 */
@Metadata(label = "validation")
@XmlRootElement(name = "endpointValidator")
@XmlAccessorType(XmlAccessType.FIELD)
public class EndpointValidatorDefinition extends ValidatorDefinition {

    @XmlAttribute
    private String ref;

    @XmlAttribute
    private String uri;

    public EndpointValidatorDefinition() {}

    public EndpointValidatorDefinition(EndpointValidatorDefinition source) {
        super(source);
        this.ref = source.ref;
        this.uri = source.ref;
    }

    @Override
    public EndpointValidatorDefinition copyDefinition() {
        return new EndpointValidatorDefinition(this);
    }

    public String getRef() {
        return ref;
    }

    /**
     * Set the reference of the Endpoint.
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Set the URI of the Endpoint.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }
}
