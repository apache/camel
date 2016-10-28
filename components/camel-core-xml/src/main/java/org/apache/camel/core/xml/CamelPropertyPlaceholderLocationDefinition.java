/**
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

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * Properties to use with properties placeholder
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "propertiesLocation")
public class CamelPropertyPlaceholderLocationDefinition extends IdentifiedType {
    @XmlAttribute(required = true)
    public String path;
    @XmlAttribute
    public String resolver;

    public String getPath() {
        return path;
    }

    /**
     * Property locations to use.
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getResolver() {
        return resolver;
    }

    /**
     * The resolver to use to locate the location
     */
    public void setResolver(String resolver) {
        this.resolver = resolver;
    }

    public String getLocation() {
        return ObjectHelper.isEmpty(resolver) ? path : resolver + path;
    }
}
