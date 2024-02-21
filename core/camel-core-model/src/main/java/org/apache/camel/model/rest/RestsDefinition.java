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

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * A series of rest services defined using the rest-dsl
 */
@Metadata(label = "rest")
@XmlRootElement(name = "rests")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestsDefinition extends OptionalIdentifiedDefinition<RestsDefinition> implements RestContainer, ResourceAware {

    @XmlElementRef
    private List<RestDefinition> rests = new ArrayList<>();
    @XmlTransient
    private Resource resource;

    public RestsDefinition() {
    }

    @Override
    public String toString() {
        return "Rests: " + rests;
    }

    @Override
    public String getShortName() {
        return "rests";
    }

    @Override
    public String getLabel() {
        return "Rest " + getId();
    }

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public List<RestDefinition> getRests() {
        return rests;
    }

    /**
     * The rest services
     */
    @Override
    public void setRests(List<RestDefinition> rests) {
        this.rests = rests;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Creates a rest DSL
     */
    public RestDefinition rest() {
        RestDefinition rest = createRest();
        return rest(rest);
    }

    /**
     * Creates a rest DSL
     *
     * @param uri the rest path
     */
    public RestDefinition rest(String uri) {
        RestDefinition rest = createRest();
        rest.setPath(uri);
        return rest(rest);
    }

    /**
     * Adds the {@link org.apache.camel.model.rest.RestsDefinition}
     */
    public RestDefinition rest(RestDefinition rest) {
        getRests().add(rest);
        return rest;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected RestDefinition createRest() {
        RestDefinition rest = new RestDefinition();
        if (resource != null) {
            rest.setResource(resource);
        }
        return rest;
    }

}
