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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * To configure global rest security requirements.
 */
@Metadata(label = "rest,security,configuration", title = "Rest Security Requirements")
@XmlRootElement(name = "securityRequirements")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestSecuritiesRequirement {
    @XmlTransient
    RestDefinition rest;

    @XmlTransient
    Map<String, SecurityDefinition> itemsMap = new HashMap<>();

    @XmlElements({ @XmlElement(name = "securityRequirement", type = SecurityDefinition.class) })
    List<SecurityDefinition> securityRequirements = new ArrayList<>();

    public RestSecuritiesRequirement() {
    }

    public RestSecuritiesRequirement(RestDefinition rest) {
        this.rest = rest;
    }

    public RestDefinition securityRequirement(String key) {
        return securityRequirement(key, null);
    }

    public RestDefinition securityRequirement(String key, String scopes) {
        SecurityDefinition requirement = itemsMap.get(key);
        if (requirement == null) {
            requirement = new SecurityDefinition();
        }

        requirement.setKey(key);
        requirement.setScopes(scopes);
        itemsMap.put(key, requirement);
        securityRequirements = new ArrayList<>(itemsMap.values());

        return rest;
    }

    public Collection<SecurityDefinition> securityRequirements() {
        return new ArrayList<>(securityRequirements);
    }

    public List<SecurityDefinition> getSecurityRequirements() {
        return securityRequirements;
    }

    /**
     * Security requirement configurations
     */
    public void setSecurityRequirements(Collection<SecurityDefinition> securityRequirements) {
        itemsMap = securityRequirements.stream()
                .collect(Collectors.toMap(SecurityDefinition::getKey, Function.identity(), (u, v) -> u));
        this.securityRequirements = new ArrayList<>(itemsMap.values());
    }
}
