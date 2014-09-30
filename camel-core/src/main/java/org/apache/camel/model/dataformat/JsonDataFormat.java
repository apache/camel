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

import org.apache.camel.CamelContext;
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
    @XmlAttribute
    private Class<?> jsonView;
    @XmlAttribute
    private String include;
    @XmlAttribute
    private Boolean allowJmsType;
    @XmlAttribute
    private String collectionTypeName;
    @XmlTransient
    private Class<?> collectionType;
    @XmlAttribute
    private Boolean useList;
    @XmlAttribute
    private Boolean enableJaxbAnnotationModule;

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

    public Class<?> getJsonView() {
        return jsonView;
    }

    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public Boolean getAllowJmsType() {
        return allowJmsType;
    }

    public void setAllowJmsType(Boolean allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    public void setCollectionTypeName(String collectionTypeName) {
        this.collectionTypeName = collectionTypeName;
    }

    public Boolean getUseList() {
        return useList;
    }

    public void setUseList(Boolean useList) {
        this.useList = useList;
    }

    public Boolean getEnableJaxbAnnotationModule() {
        return enableJaxbAnnotationModule;
    }

    public void setEnableJaxbAnnotationModule(Boolean enableJaxbAnnotationModule) {
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (library == JsonLibrary.XStream) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-xstream");
        } else if (library == JsonLibrary.Jackson) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-jackson");
        } else {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-gson");
        }

        if (unmarshalType == null && unmarshalTypeName != null) {
            try {
                unmarshalType = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(unmarshalTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
        if (collectionType == null && collectionTypeName != null) {
            try {
                collectionType = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(collectionTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (unmarshalType != null) {
            setProperty(camelContext, dataFormat, "unmarshalType", unmarshalType);
        }
        if (prettyPrint != null) {
            setProperty(camelContext, dataFormat, "prettyPrint", prettyPrint);
        }
        if (jsonView != null) {
            setProperty(camelContext, dataFormat, "jsonView", jsonView);
        }
        if (include != null) {
            setProperty(camelContext, dataFormat, "include", include);
        }
        if (allowJmsType != null) {
            setProperty(camelContext, dataFormat, "allowJmsType", allowJmsType);
        }
        if (collectionType != null) {
            setProperty(camelContext, dataFormat, "collectionType", collectionType);
        }
        if (useList != null) {
            setProperty(camelContext, dataFormat, "useList", useList);
        }
        if (enableJaxbAnnotationModule != null) {
            setProperty(camelContext, dataFormat, "enableJaxbAnnotationModule", enableJaxbAnnotationModule);
        }
    }

}
