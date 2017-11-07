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

import java.text.DateFormat;
import java.util.TimeZone;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;

/**
 * JSon data format is used for unmarshal a JSon payload to POJO or to marshal
 * POJO back to JSon payload.
 *
 * @version
 */
@Metadata(label = "dataformat,transformation,json", title = "JSon")
@XmlRootElement(name = "json")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String objectMapper;
    @XmlAttribute
    private Boolean prettyPrint;
    @XmlAttribute
    @Metadata(defaultValue = "XStream")
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
    @XmlAttribute
    private String moduleClassNames;
    @XmlAttribute
    private String moduleRefs;
    @XmlAttribute
    private String enableFeatures;
    @XmlAttribute
    private String disableFeatures;
    @XmlAttribute
    private String permissions;
    @XmlAttribute
    private Boolean allowUnmarshallType;
    @XmlAttribute
    private String timezone;
    
    public JsonDataFormat() {
        super("json");
    }

    public JsonDataFormat(JsonLibrary library) {
        this.library = library;
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    /**
     * Lookup and use the existing ObjectMapper with the given id when using
     * Jackson.
     */
    public void setObjectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Boolean getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * To enable pretty printing output nicely formatted.
     * <p/>
     * Is by default false.
     */
    public void setPrettyPrint(Boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class name of the java type to use when unarmshalling
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the java type to use when unarmshalling
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public JsonLibrary getLibrary() {
        return library;
    }

    /**
     * Which json library to use.
     */
    public void setLibrary(JsonLibrary library) {
        this.library = library;
    }

    public Class<?> getJsonView() {
        return jsonView;
    }

    /**
     * When marshalling a POJO to JSON you might want to exclude certain fields
     * from the JSON output. With Jackson you can use JSON views to accomplish
     * this. This option is to refer to the class which has @JsonView
     * annotations
     */
    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
    }

    public String getInclude() {
        return include;
    }

    /**
     * If you want to marshal a pojo to JSON, and the pojo has some fields with
     * null values. And you want to skip these null values, you can set this
     * option to <tt>NOT_NULL</tt>
     */
    public void setInclude(String include) {
        this.include = include;
    }

    public Boolean getAllowJmsType() {
        return allowJmsType;
    }

    /**
     * Used for JMS users to allow the JMSType header from the JMS spec to
     * specify a FQN classname to use to unmarshal to.
     */
    public void setAllowJmsType(Boolean allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    /**
     * Refers to a custom collection type to lookup in the registry to use. This
     * option should rarely be used, but allows to use different collection
     * types than java.util.Collection based as default.
     */
    public void setCollectionTypeName(String collectionTypeName) {
        this.collectionTypeName = collectionTypeName;
    }

    public Boolean getUseList() {
        return useList;
    }

    /**
     * To unarmshal to a List of Map or a List of Pojo.
     */
    public void setUseList(Boolean useList) {
        this.useList = useList;
    }

    public Boolean getEnableJaxbAnnotationModule() {
        return enableJaxbAnnotationModule;
    }

    /**
     * Whether to enable the JAXB annotations module when using jackson. When
     * enabled then JAXB annotations can be used by Jackson.
     */
    public void setEnableJaxbAnnotationModule(Boolean enableJaxbAnnotationModule) {
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    public String getModuleClassNames() {
        return moduleClassNames;
    }

    /**
     * To use custom Jackson modules com.fasterxml.jackson.databind.Module
     * specified as a String with FQN class names. Multiple classes can be
     * separated by comma.
     */
    public void setModuleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
    }

    public String getModuleRefs() {
        return moduleRefs;
    }

    /**
     * To use custom Jackson modules referred from the Camel registry. Multiple
     * modules can be separated by comma.
     */
    public void setModuleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    /**
     * Set of features to enable on the Jackson
     * <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
     * <p/>
     * The features should be a name that matches a enum from
     * <tt>com.fasterxml.jackson.databind.SerializationFeature</tt>,
     * <tt>com.fasterxml.jackson.databind.DeserializationFeature</tt>, or
     * <tt>com.fasterxml.jackson.databind.MapperFeature</tt>
     * <p/>
     * Multiple features can be separated by comma
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson
     * <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
     * <p/>
     * The features should be a name that matches a enum from
     * <tt>com.fasterxml.jackson.databind.SerializationFeature</tt>,
     * <tt>com.fasterxml.jackson.databind.DeserializationFeature</tt>, or
     * <tt>com.fasterxml.jackson.databind.MapperFeature</tt>
     * <p/>
     * Multiple features can be separated by comma
     */
    public void setDisableFeatures(String disableFeatures) {
        this.disableFeatures = disableFeatures;
    }

    public String getPermissions() {
        return permissions;
    }

    /**
     * Adds permissions that controls which Java packages and classes XStream is
     * allowed to use during unmarshal from xml/json to Java beans.
     * <p/>
     * A permission must be configured either here or globally using a JVM
     * system property. The permission can be specified in a syntax where a plus
     * sign is allow, and minus sign is deny. <br/>
     * Wildcards is supported by using <tt>.*</tt> as prefix. For example to
     * allow <tt>com.foo</tt> and all subpackages then specfy
     * <tt>+com.foo.*</tt>. Multiple permissions can be configured separated by
     * comma, such as <tt>+com.foo.*,-com.foo.bar.MySecretBean</tt>. <br/>
     * The following default permission is always included:
     * <tt>"-*,java.lang.*,java.util.*"</tt> unless its overridden by specifying
     * a JVM system property with they key
     * <tt>org.apache.camel.xstream.permissions</tt>.
     */
    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    /**
     * To add permission for the given pojo classes.
     * 
     * @param type the pojo class(es) xstream should use as allowed permission
     * @see #setPermissions(String)
     */
    public void setPermissions(Class<?>... type) {
        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (Class<?> clazz : type) {
            csb.append("+");
            csb.append(clazz.getName());
        }
        setPermissions(csb.toString());
    }

    public Boolean getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then Jackson is allowed to attempt to use the
     * CamelJacksonUnmarshalType header during the unmarshalling.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setAllowUnmarshallType(Boolean allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    public String getTimezone() {
        return timezone;
    }

    /**
     * If set then Jackson will use the Timezone when marshalling/unmarshalling.
     * This option will have no effect on the others Json DataFormat, like gson,
     * fastjson and xstream.
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    @Override
    public String getDataFormatName() {
        // json data format is special as the name can be from different bundles
        return "json-" + library.name().toLowerCase();
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (library == JsonLibrary.XStream) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-xstream");
        } else if (library == JsonLibrary.Jackson) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-jackson");
        } else if (library == JsonLibrary.Gson) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-gson");
        } else if (library == JsonLibrary.Fastjson) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-fastjson");
        } else {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-johnzon");
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
        if (objectMapper != null) {
            // must be a reference value
            String ref = objectMapper.startsWith("#") ? objectMapper : "#" + objectMapper;
            setProperty(camelContext, dataFormat, "objectMapper", ref);
        }
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
        if (moduleClassNames != null) {
            setProperty(camelContext, dataFormat, "moduleClassNames", moduleClassNames);
        }
        if (moduleRefs != null) {
            setProperty(camelContext, dataFormat, "moduleRefs", moduleRefs);
        }
        if (enableFeatures != null) {
            setProperty(camelContext, dataFormat, "enableFeatures", enableFeatures);
        }
        if (disableFeatures != null) {
            setProperty(camelContext, dataFormat, "disableFeatures", disableFeatures);
        }
        if (permissions != null) {
            setProperty(camelContext, dataFormat, "permissions", permissions);
        }
        if (allowUnmarshallType != null) {
            setProperty(camelContext, dataFormat, "allowUnmarshallType", allowUnmarshallType);
        }
        // if we have the unmarshal type, but no permission set, then use it to
        // be allowed
        if (permissions == null && unmarshalType != null) {
            String allow = "+" + unmarshalType.getName();
            setProperty(camelContext, dataFormat, "permissions", allow);
        }
    }

}
