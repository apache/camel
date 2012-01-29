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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the Json {@link DataFormat}
 *
 * @version 
 */
@XmlRootElement(name = "json")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private Boolean prettyPrint;
    @XmlAttribute
    private JsonLibrary library = JsonLibrary.XStream;
    @XmlAttribute
    private String unmarshalTypeName;
    @XmlTransient
    private Class<?> unmarshalType;

    public JsonDataFormat() {
    }

    public JsonDataFormat(JsonLibrary library) {
        this.library = library;
    }

    public Boolean getPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(Boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public JsonLibrary getLibrary() {
        return library;
    }

    public void setLibrary(JsonLibrary library) {
        this.library = library;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (library == JsonLibrary.XStream) {
            setProperty(this, "dataFormatName", "json-xstream");
        } else if(library == JsonLibrary.Jackson){
            setProperty(this, "dataFormatName", "json-jackson");
        } else {
            setProperty(this, "dataFormatName", "json-gson");
        }

        if (unmarshalType == null && unmarshalTypeName != null) {
            try {
                unmarshalType = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(unmarshalTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        if (unmarshalType != null) {
            setProperty(dataFormat, "unmarshalType", unmarshalType);
        }
        if (prettyPrint != null) {
            setProperty(dataFormat, "prettyPrint", unmarshalType);
        }
    }

}
