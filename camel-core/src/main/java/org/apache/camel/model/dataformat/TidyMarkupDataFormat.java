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

import org.w3c.dom.Node;

import org.apache.camel.spi.DataFormat;


/**
 * Represents a wellformed HTML document (XML well Formed) {@link DataFormat}
 *
 */
@XmlRootElement(name = "tidyMarkup")
@XmlAccessorType(XmlAccessType.FIELD)
public class TidyMarkupDataFormat extends DataFormatType {

    @XmlAttribute(required = false)
    private Class<?> dataObjectType;

    public TidyMarkupDataFormat() {
        super("org.apache.camel.dataformat.tagsoup.TidyMarkupDataFormat");
        this.setDataObjectType(Node.class);
    }

    public TidyMarkupDataFormat(Class<?> dataObjectType) {
        this();
        if (!dataObjectType.isAssignableFrom(String.class) && !dataObjectType.isAssignableFrom(Node.class)) {
            throw new IllegalArgumentException("TidyMarkupDataFormat only supports returning a String or a org.w3c.dom.Node object");
        }
        this.setDataObjectType(dataObjectType);
    }

    public void setDataObjectType(Class<?> dataObjectType) {
        this.dataObjectType = dataObjectType;
    }

    public Class<?> getDataObjectType() {
        return dataObjectType;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        Class<?> type = getDataObjectType();
        if (type != null) {
            setProperty(dataFormat, "dataObjectType", type);
        }
    }

}