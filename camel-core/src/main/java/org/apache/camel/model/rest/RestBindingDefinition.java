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
public class RestBindingDefinition extends NoOutputDefinition {

    @XmlAttribute
    private String consumes;

    @XmlAttribute
    private String produces;

    @XmlAttribute
    private RestBindingMode bindingMode;

    @XmlAttribute
    private String jsonDataFormat;

    @XmlAttribute
    private String xmlDataFormat;

    @XmlAttribute
    private String type;

    @XmlAttribute
    private String outType;

    @XmlAttribute
    private Boolean list;

    @XmlAttribute
    private Boolean outList;

    @Override
    public String toString() {
        return "RestBinding";
    }

    @Override
    public String getShortName() {
        return "rest";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {

        CamelContext context = routeContext.getCamelContext();

        // the default binding mode can be overridden per rest verb
        String mode = context.getRestConfiguration().getBindingMode().name();
        if (bindingMode != null) {
            mode = bindingMode.name();
        }

        if (mode == null || "off".equals(mode)) {
            // binding mode is off, so create a off mode binding processor
            return new RestBindingProcessor(null, null, null, null, consumes, produces, mode);
        }

        // setup json data format
        String name = jsonDataFormat;
        if (name == null) {
            name = "json-jackson";
        }
        DataFormat json = context.resolveDataFormat(name);

        // is json binding required?
        if (mode.contains("json") && json == null) {
            throw new IllegalArgumentException("JSon DataFormat " + name + " not found.");
        }
        Class<?> clazz = null;
        if (type != null) {
            clazz = context.getClassResolver().resolveMandatoryClass(type);
        }
        if (clazz != null) {
            IntrospectionSupport.setProperty(context.getTypeConverter(), json, "unmarshalType", clazz);
            IntrospectionSupport.setProperty(context.getTypeConverter(), json, "useList", list != null ? list : false);
        }
        context.addService(json);

        DataFormat outJson = context.resolveDataFormat(name);
        Class<?> outClazz = null;
        if (outType != null) {
            outClazz = context.getClassResolver().resolveMandatoryClass(outType);
        }
        if (outClazz != null) {
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "unmarshalType", outClazz);
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "useList", outList != null ? outList : false);
        }
        context.addService(outJson);

        // setup xml data format
        name = xmlDataFormat;
        if (name == null) {
            name = "jaxb";
        }
        DataFormat jaxb = context.resolveDataFormat(name);
        // is xml binding required?
        if (mode.contains("xml") && jaxb == null) {
            throw new IllegalArgumentException("XML DataFormat " + name + " not found.");
        }
        clazz = null;
        if (type != null) {
            clazz = context.getClassResolver().resolveMandatoryClass(type);
        }
        if (clazz != null) {
            JAXBContext jc = JAXBContext.newInstance(clazz);
            IntrospectionSupport.setProperty(context.getTypeConverter(), jaxb, "context", jc);
        }
        context.addService(jaxb);

        DataFormat outJaxb = context.resolveDataFormat(name);
        outClazz = null;
        if (outType != null) {
            outClazz = context.getClassResolver().resolveMandatoryClass(outType);
        }
        if (outClazz != null) {
            JAXBContext jc = JAXBContext.newInstance(outClazz);
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJaxb, "context", jc);
        } else if (clazz != null) {
            // fallback and use the context from the input
            JAXBContext jc = JAXBContext.newInstance(clazz);
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJaxb, "context", jc);
        }
        context.addService(outJaxb);

        return new RestBindingProcessor(json, jaxb, outJson, outJaxb, consumes, produces, mode);
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

    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    public void setJsonDataFormat(String jsonDataFormat) {
        this.jsonDataFormat = jsonDataFormat;
    }

    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    public void setXmlDataFormat(String xmlDataFormat) {
        this.xmlDataFormat = xmlDataFormat;
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

    public Boolean getList() {
        return list;
    }

    public void setList(Boolean list) {
        this.list = list;
    }

    public Boolean getOutList() {
        return outList;
    }

    public void setOutList(Boolean outList) {
        this.outList = outList;
    }
}
