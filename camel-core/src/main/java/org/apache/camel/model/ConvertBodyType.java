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

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.ConvertBodyProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;convertBodyTo/&gt; element
 */
@XmlRootElement(name = "convertBodyTo")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConvertBodyType extends ProcessorType<ProcessorType> {
    @XmlAttribute
    private String type;
    @XmlTransient
    private Class typeClass;

    public ConvertBodyType() {
    }

    public ConvertBodyType(String type) {
        setType(type);
    }

    public ConvertBodyType(Class typeClass) {
        setTypeClass(typeClass);
    }

    @Override
    public String toString() {
        return "convertBodyTo[ " + getType() + "]";
    }

    @Override
    public String getShortName() {
        return "convertBodyTo";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return new ConvertBodyProcessor(getTypeClass());
    }

    @Override
    public List<ProcessorType<?>> getOutputs() {
        return Collections.EMPTY_LIST;
    }    
    
    protected Class createTypeClass() {
        return ObjectHelper.loadClass(getType(), getClass().getClassLoader());
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setTypeClass(Class typeClass) {
        this.typeClass = typeClass;
    }

    public Class getTypeClass() {
        if (typeClass == null) {
            setTypeClass(createTypeClass());
        }
        return typeClass;
    }
}
