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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;removeProperty/&gt; element
 */
@XmlRootElement(name = "removeProperty")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemovePropertyType extends OutputType<ProcessorType> {
    @XmlAttribute(required = true)
    private String propertyName;   
    
    public RemovePropertyType() {
    }
    
    public RemovePropertyType(String propertyName) {
        setPropertyName(propertyName);
    }
    
    @Override
    public String toString() {
        return "RemoveProperty[" + getPropertyName() + "]";
    }

    @Override
    public String getShortName() {
        return "removeProperty";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {        
        ObjectHelper.notNull(getPropertyName(), "propertyName");
        return ProcessorBuilder.removeProperty(getPropertyName());
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
