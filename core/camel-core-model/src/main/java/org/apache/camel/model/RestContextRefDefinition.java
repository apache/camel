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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To refer to an XML file with rest services defined using the rest-dsl
 */
@Metadata(label = "configuration,rest")
@XmlRootElement(name = "restContextRef")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestContextRefDefinition {
    @XmlAttribute(required = true)
    private String ref;

    public RestContextRefDefinition() {
    }

    @Override
    public String toString() {
        return "RestContextRef[" + getRef() + "]";
    }

    public String getRef() {
        return ref;
    }

    /**
     * Reference to the rest-dsl
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public List<RestDefinition> lookupRests(CamelContext camelContext) {
        return RestContextRefDefinitionHelper.lookupRests(camelContext, ref);
    }

}
