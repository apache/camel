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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.NoOutputDefinition;
import org.apache.camel.processor.binding.RestBindingProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;

@XmlRootElement(name = "restBinding")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestBindingDefinition extends NoOutputDefinition<RestBindingDefinition> {

    @XmlAttribute
    private String consumes;

    @XmlAttribute
    private String produces;

    @XmlAttribute
    private RestBindingMode bindingMode;

    @XmlAttribute
    private String type;

    @XmlAttribute
    private String outType;

    @XmlAttribute
    private Boolean skipBindingOnErrorCode;

    @Override
    public String toString() {
        return "RestBinding";
    }

    @Override
    public String getShortName() {
        return "restBinding";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {

        CamelContext context = routeContext.getCamelContext();

        // the default binding mode can be overridden per rest verb
        String mode = context.getRestConfiguration().getBindingMode().name();
        if (bindingMode != null) {
            mode = bindingMode.name();
        }

        // skip by default
        boolean skip = skipBindingOnErrorCode == null || skipBindingOnErrorCode;

        if (mode == null || "off".equals(mode)) {
            // binding mode is off, so create a off mode binding processor
            return new RestBindingProcessor(null, null, null, null, consumes, produces, mode, skip);
        }

        // setup json data format
        String name = context.getRestConfiguration().getJsonDataFormat();
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
        DataFormat json = context.resolveDataFormat(name);
        DataFormat outJson = context.resolveDataFormat(name);

        // is json binding required?
        if (mode.contains("json") && json == null) {
            throw new IllegalArgumentException("JSon DataFormat " + name + " not found.");
        }

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
            setAdditionalConfiguration(context, json);
            context.addService(json);

            Class<?> outClazz = null;
            if (outType != null) {
                String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
                outClazz = context.getClassResolver().resolveMandatoryClass(typeName);
            }
            if (outClazz != null) {
                IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "unmarshalType", outClazz);
                IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "useList", outType.endsWith("[]"));
            }
            setAdditionalConfiguration(context, outJson);
            context.addService(outJson);
        }

        // setup xml data format
        name = context.getRestConfiguration().getXmlDataFormat();
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
        DataFormat jaxb = context.resolveDataFormat(name);
        DataFormat outJaxb = context.resolveDataFormat(name);

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
            if (context.getRestConfiguration().getDataFormatProperties() != null) {
                IntrospectionSupport.setProperties(context.getTypeConverter(), jaxb, context.getRestConfiguration().getDataFormatProperties());
            }
            setAdditionalConfiguration(context, jaxb);
            context.addService(jaxb);

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
            setAdditionalConfiguration(context, outJaxb);
            context.addService(outJaxb);
        }

        return new RestBindingProcessor(json, jaxb, outJson, outJaxb, consumes, produces, mode, skip);
    }

    private void setAdditionalConfiguration(CamelContext context, DataFormat dataFormat) throws Exception {
        if (context.getRestConfiguration().getDataFormatProperties() != null && !context.getRestConfiguration().getDataFormatProperties().isEmpty()) {
            // must use a copy as otherwise the options gets removed during introspection setProperties
            Map<String, Object> copy = new HashMap<String, Object>();
            copy.putAll(context.getRestConfiguration().getDataFormatProperties());

            IntrospectionSupport.setProperties(context.getTypeConverter(), dataFormat, copy);
        }
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    public Boolean getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    public void setSkipBindingOnErrorCode(Boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }
}
