/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the <a href="http://activemq.apache.org/camel/artix-data-services.html">Artix Data Services</a>
 * {@link DataFormat}
 *
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "artixDS")
@XmlAccessorType(XmlAccessType.FIELD)
public class ArtixDSDataFormat extends DataFormatType {
    @XmlAttribute(required = false)
    private String elementTypeName;
    @XmlAttribute(required = false)
    private String format;
    @XmlAttribute(required = false)
    private Class<?> elementType;
    @XmlAttribute(required = false)
    private ArtixDSContentType contentType;

    public ArtixDSDataFormat() {
        super("org.apache.camel.artix.ds.ArtixDSFormat");
    }

    public ArtixDSDataFormat(Class<?> elementType) {
        this();
        this.elementType = elementType;
    }

    public ArtixDSDataFormat(Class<?> elementType, ArtixDSContentType contentType) {
        this();
        this.elementType = elementType;
        this.contentType = contentType;
    }

    public ArtixDSDataFormat(ArtixDSContentType contentType) {
        this();
        this.contentType = contentType;
    }

    // Properties
    //-------------------------------------------------------------------------

    public String getElementTypeName() {
        return elementTypeName;
    }

    public void setElementTypeName(String elementTypeName) {
        this.elementTypeName = elementTypeName;
    }

    public ArtixDSContentType getContentType() {
        return contentType;
    }

    public void setContentType(ArtixDSContentType contentType) {
        this.contentType = contentType;
    }

    public Class<?> getElementType() {
        if (elementType == null) {
            if (elementTypeName != null) {
                elementType = ObjectHelper.loadClass(elementTypeName, getClass().getClassLoader());
            }
        }
        return elementType;
    }

    public void setElementType(Class<?> elementType) {
        this.elementType = elementType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        Class<?> type = getElementType();
        if (type != null) {
            setProperty(dataFormat, "elementType", type);
        }
        ArtixDSContentType content = getContentType();
        if (content != null) {
            setProperty(dataFormat, "contentType", content);
        }
    }
}