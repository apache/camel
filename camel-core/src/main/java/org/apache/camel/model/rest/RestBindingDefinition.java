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
package org.apache.camel.model.rest;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.processor.RestBindingAdvice;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;

/**
 * To configure rest binding
 */
@Metadata(label = "rest")
@XmlRootElement(name = "restBinding")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestBindingDefinition extends OptionalIdentifiedDefinition<RestBindingDefinition> {

    @XmlTransient
    private Map<String, String> defaultValues;

    @XmlAttribute
    private String consumes;

    @XmlAttribute
    private String produces;

    @XmlAttribute
    @Metadata(defaultValue = "off")
    private RestBindingMode bindingMode;

    @XmlAttribute
    private String type;

    @XmlAttribute
    private String outType;

    @XmlAttribute
    private Boolean skipBindingOnErrorCode;

    @XmlAttribute
    private Boolean enableCORS;

    @XmlAttribute
    private String component;

    public RestBindingDefinition() {
    }

    @Override
    public String toString() {
        return "RestBinding";
    }

    public RestBindingAdvice createRestBindingAdvice(RouteContext routeContext) throws Exception {

        CamelContext context = routeContext.getCamelContext();
        RestConfiguration config = context.getRestConfiguration(component, true);

        // these options can be overridden per rest verb
        String mode = config.getBindingMode().name();
        if (bindingMode != null) {
            mode = bindingMode.name();
        }
        boolean cors = config.isEnableCORS();
        if (enableCORS != null) {
            cors = enableCORS;
        }
        boolean skip = config.isSkipBindingOnErrorCode();
        if (skipBindingOnErrorCode != null) {
            skip = skipBindingOnErrorCode;
        }

        // cors headers
        Map<String, String> corsHeaders = config.getCorsHeaders();

        if (mode == null || "off".equals(mode)) {
            // binding mode is off, so create a off mode binding processor
            return new RestBindingAdvice(context, null, null, null, null, consumes, produces, mode, skip, cors, corsHeaders, defaultValues);
        }

        // setup json data format
        DataFormat json = null;
        DataFormat outJson = null;
        if (mode.contains("json") || "auto".equals(mode)) {
            String name = config.getJsonDataFormat();
            if (name != null) {
                // must only be a name, not refer to an existing instance
                Object instance = context.getRegistry().lookupByName(name);
                if (instance != null) {
                    throw new IllegalArgumentException("JsonDataFormat name: " + name + " must not be an existing bean instance from the registry");
                }
            } else {
                name = "json-jackson";
            }
            // this will create a new instance as the name was not already pre-created
            json = context.resolveDataFormat(name);
            outJson = context.resolveDataFormat(name);

            if (json != null) {
                Class<?> clazz = null;
                if (type != null) {
                    String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
                    clazz = context.getClassResolver().resolveMandatoryClass(typeName);
                }
                if (clazz != null) {
                    IntrospectionSupport.setProperty(context.getTypeConverter(), json, "unmarshalType", clazz);
                    IntrospectionSupport.setProperty(context.getTypeConverter(), json, "useList", type.endsWith("[]"));
                }
                setAdditionalConfiguration(config, context, json, "json.in.");

                Class<?> outClazz = null;
                if (outType != null) {
                    String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
                    outClazz = context.getClassResolver().resolveMandatoryClass(typeName);
                }
                if (outClazz != null) {
                    IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "unmarshalType", outClazz);
                    IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "useList", outType.endsWith("[]"));
                }
                setAdditionalConfiguration(config, context, outJson, "json.out.");
            }
        }

        // setup xml data format
        DataFormat jaxb = null;
        DataFormat outJaxb = null;
        if (mode.contains("xml") || "auto".equals(mode)) {
            String name = config.getXmlDataFormat();
            if (name != null) {
                // must only be a name, not refer to an existing instance
                Object instance = context.getRegistry().lookupByName(name);
                if (instance != null) {
                    throw new IllegalArgumentException("XmlDataFormat name: " + name + " must not be an existing bean instance from the registry");
                }
            } else {
                name = "jaxb";
            }
            // this will create a new instance as the name was not already pre-created
            jaxb = context.resolveDataFormat(name);
            outJaxb = context.resolveDataFormat(name);

            // is xml binding required?
            if (mode.contains("xml") && jaxb == null) {
                throw new IllegalArgumentException("XML DataFormat " + name + " not found.");
            }

            if (jaxb != null) {
                Class<?> clazz = null;
                if (type != null) {
                    String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
                    clazz = context.getClassResolver().resolveMandatoryClass(typeName);
                }
                if (clazz != null) {
                    JAXBContext jc = JAXBContext.newInstance(clazz);
                    IntrospectionSupport.setProperty(context.getTypeConverter(), jaxb, "context", jc);
                }
                setAdditionalConfiguration(config, context, jaxb, "xml.in.");

                Class<?> outClazz = null;
                if (outType != null) {
                    String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
                    outClazz = context.getClassResolver().resolveMandatoryClass(typeName);
                }
                if (outClazz != null) {
                    JAXBContext jc = JAXBContext.newInstance(outClazz);
                    IntrospectionSupport.setProperty(context.getTypeConverter(), outJaxb, "context", jc);
                } else if (clazz != null) {
                    // fallback and use the context from the input
                    JAXBContext jc = JAXBContext.newInstance(clazz);
                    IntrospectionSupport.setProperty(context.getTypeConverter(), outJaxb, "context", jc);
                }
                setAdditionalConfiguration(config, context, outJaxb, "xml.out.");
            }
        }

        return new RestBindingAdvice(context, json, jaxb, outJson, outJaxb, consumes, produces, mode, skip, cors, corsHeaders, defaultValues);
    }

    private void setAdditionalConfiguration(RestConfiguration config, CamelContext context,
                                            DataFormat dataFormat, String prefix) throws Exception {
        if (config.getDataFormatProperties() != null && !config.getDataFormatProperties().isEmpty()) {
            // must use a copy as otherwise the options gets removed during introspection setProperties
            Map<String, Object> copy = new HashMap<String, Object>();

            // filter keys on prefix
            // - either its a known prefix and must match the prefix parameter
            // - or its a common configuration that we should always use
            for (Map.Entry<String, Object> entry : config.getDataFormatProperties().entrySet()) {
                String key = entry.getKey();
                String copyKey;
                boolean known = isKeyKnownPrefix(key);
                if (known) {
                    // remove the prefix from the key to use
                    copyKey = key.substring(prefix.length());
                } else {
                    // use the key as is
                    copyKey = key;
                }
                if (!known || key.startsWith(prefix)) {
                    copy.put(copyKey, entry.getValue());
                }
            }

            // set reference properties first as they use # syntax that fools the regular properties setter
            EndpointHelper.setReferenceProperties(context, dataFormat, copy);
            EndpointHelper.setProperties(context, dataFormat, copy);
        }
    }

    private boolean isKeyKnownPrefix(String key) {
        return key.startsWith("json.in.") || key.startsWith("json.out.") || key.startsWith("xml.in.") || key.startsWith("xml.out.");
    }

    public String getConsumes() {
        return consumes;
    }

    /**
     * Adds a default value for the query parameter
     *
     * @param paramName   query parameter name
     * @param defaultValue the default value
     */
    public void addDefaultValue(String paramName, String defaultValue) {
        if (defaultValues == null) {
            defaultValues = new HashMap<String, String>();
        }
        defaultValues.put(paramName, defaultValue);
    }

    /**
     * Gets the registered default values for query parameters
     */
    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    /**
     * Sets the component name that this definition will apply to  
     */
    public void setComponent(String component) {
        this.component = component;
    }

    public String getComponent() {
        return component;
    }

    /**
     * To define the content type what the REST service consumes (accept as input), such as application/xml or application/json
     */
    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    /**
     * To define the content type what the REST service produces (uses for output), such as application/xml or application/json
     */
    public void setProduces(String produces) {
        this.produces = produces;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    /**
     * Sets the binding mode to use.
     * <p/>
     * The default value is off
     */
    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getType() {
        return type;
    }

    /**
     * Sets the class name to use for binding from input to POJO for the incoming data
     * <p/>
     * The canonical name of the class of the input data. Append a [] to the end of the canonical name
     * if you want the input to be an array type.
     */
    public void setType(String type) {
        this.type = type;
    }

    public String getOutType() {
        return outType;
    }

    /**
     * Sets the class name to use for binding from POJO to output for the outgoing data
     * <p/>
     * The canonical name of the class of the input data. Append a [] to the end of the canonical name
     * if you want the input to be an array type.
     */
    public void setOutType(String outType) {
        this.outType = outType;
    }

    public Boolean getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    /**
     * Whether to skip binding on output if there is a custom HTTP error code header.
     * This allows to build custom error messages that do not bind to json / xml etc, as success messages otherwise will do.
     */
    public void setSkipBindingOnErrorCode(Boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public Boolean getEnableCORS() {
        return enableCORS;
    }

    /**
     * Whether to enable CORS headers in the HTTP response.
     * <p/>
     * The default value is false.
     */
    public void setEnableCORS(Boolean enableCORS) {
        this.enableCORS = enableCORS;
    }

    @Override
    public String getLabel() {
        return "";
    }
}
