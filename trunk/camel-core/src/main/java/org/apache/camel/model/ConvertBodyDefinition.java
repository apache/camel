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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.processor.ConvertBodyProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;convertBodyTo/&gt; element
 */
@XmlRootElement(name = "convertBodyTo")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConvertBodyDefinition extends ProcessorDefinition<ConvertBodyDefinition> {
    @XmlAttribute
    private String type;
    @XmlAttribute(required = false)
    private String charset;
    @XmlTransient
    private Class<?> typeClass;

    public ConvertBodyDefinition() {
    }

    public ConvertBodyDefinition(String type) {
        setType(type);
    }

    public ConvertBodyDefinition(Class<?> typeClass) {
        setTypeClass(typeClass);
        setType(typeClass.getName());
    }

    public ConvertBodyDefinition(Class<?> typeClass, String charset) {
        setTypeClass(typeClass);
        setType(typeClass.getName());
        setCharset(charset);
    }

    @Override
    public String toString() {        
        return "convertBodyTo[" + getType() + "]";
    }

    @Override
    public String getShortName() {
        return "convertBodyTo";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (getTypeClass() == null) {
            this.typeClass = routeContext.getCamelContext().getClassResolver().resolveClass(getType());
            if (getTypeClass() == null) {
                throw new RuntimeCamelException("Cannot load the class with the class name: " + getType());
            }
        }

        // validate charset
        if (charset != null) {
            IOConverter.validateCharset(charset);
        }

        return new ConvertBodyProcessor(getTypeClass(), getCharset());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProcessorDefinition> getOutputs() {
        return Collections.EMPTY_LIST;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Class<?> getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(Class<?> typeClass) {
        this.typeClass = typeClass;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
}
