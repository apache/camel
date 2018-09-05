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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * String data format is a textual based format that supports character encoding.
 *
 * @version 
 */
@Metadata(firstVersion = "2.12.0", label = "dataformat,transformation,core", title = "String Encoding")
@XmlRootElement(name = "string")
@XmlAccessorType(XmlAccessType.FIELD)
public class StringDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String charset;

    public StringDataFormat() {
        super("string");
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        return new org.apache.camel.impl.StringDataFormat(charset);
    }

    public String getCharset() {
        return charset;
    }

    /**
     * Sets an encoding to use.
     * <p/>
     * Will by default use the JVM platform default charset.
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

}
