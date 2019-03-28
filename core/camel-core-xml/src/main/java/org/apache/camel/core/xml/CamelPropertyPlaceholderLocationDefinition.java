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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.component.properties.PropertiesLocation;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * Properties to use with properties placeholder
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "propertiesLocation")
public class CamelPropertyPlaceholderLocationDefinition extends IdentifiedType {
    @XmlAttribute @Metadata(defaultValue = "classpath")
    public String resolver;
    @XmlAttribute(required = true)
    public String path;
    @XmlAttribute @Metadata(defaultValue = "false")
    public Boolean optional;


    public String getResolver() {
        return resolver;
    }

    /**
     * The resolver to use to locate the location
     */
    public void setResolver(String resolver) {
        this.resolver = resolver;
    }

    public String getPath() {
        return path;
    }

    /**
     * Property locations to use.
     */
    public void setPath(String path) {
        this.path = path;
    }

    public Boolean getOptional() {
        return optional;
    }

    /**
     * If the location is optional.
     */
    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    @Override
    public String toString() {
        String answer = path;
        if (ObjectHelper.isNotEmpty(resolver)) {
            answer = resolver + ":" + answer;
        }
        if (ObjectHelper.isNotEmpty(optional)) {
            answer = answer + ";optional=true";
        }

        return answer;
    }

    public PropertiesLocation toLocation() {
        return new PropertiesLocation(
            resolver != null ? resolver : "classpath",
            path,
            optional != null ? optional : false
        );
    }
}
