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

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.ConvertBodyProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Converts the message body to another type
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "convertBodyTo")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConvertBodyDefinition extends NoOutputDefinition<ConvertBodyDefinition> {
    @XmlAttribute(required = true)
    private String type;
    @XmlAttribute
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
        setType(typeClass.getCanonicalName());
    }

    public ConvertBodyDefinition(Class<?> typeClass, String charset) {
        setTypeClass(typeClass);
        setType(typeClass.getCanonicalName());
        setCharset(charset);
    }

    @Override
    public String toString() {        
        return "ConvertBodyTo[" + getType() + "]";
    }

    @Override
    public String getLabel() {
        return "convertBodyTo[" + getType() + "]";
    }
    
    public static void validateCharset(String charset) throws UnsupportedCharsetException {
        if (charset != null) {
            if (Charset.isSupported(charset)) {
                Charset.forName(charset);
                return;
            }
        }
        throw new UnsupportedCharsetException(charset);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (typeClass == null && type != null) {
            typeClass = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(type);
        }

        // validate charset
        if (charset != null) {
            validateCharset(charset);
        }

        return new ConvertBodyProcessor(getTypeClass(), getCharset());
    }

    public String getType() {
        return type;
    }

    /**
     * The java type to convert to
     */
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

    /**
     * To use a specific charset when converting
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }
}
