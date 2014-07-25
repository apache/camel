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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.NoOutputDefinition;
import org.apache.camel.processor.binding.RestBindingProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

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
    private String typeList;

    @XmlTransient
    private Class<?> classType;

    @XmlTransient
    private boolean useList;

    @XmlAttribute
    private String outType;

    @XmlAttribute
    private String outTypeList;

    @XmlTransient
    private Class<?> outClassType;

    @XmlTransient
    private boolean outUseList;

    @Override
    public String toString() {
        return "RestBinding";
    }

    @Override
    public String getShortName() {
        return "rest";
    }

    // TODO: allow to configure if json/xml only or auto detect (now)

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // type must be set
        if (ObjectHelper.isEmpty(type) && ObjectHelper.isEmpty(classType)) {
            throw new IllegalArgumentException("Type must be configured on " + this);
        }

        CamelContext context = routeContext.getCamelContext();

        // the default binding mode can be overridden per rest verb
        String mode = context.getRestConfiguration().getBindingMode().name();
        if (bindingMode != null) {
            mode = bindingMode.name();
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
        if (classType == null && type != null) {
            classType = context.getClassResolver().resolveMandatoryClass(type);
        }
        if (classType == null && typeList != null) {
            classType = context.getClassResolver().resolveMandatoryClass(typeList);
        }
        if (classType != null) {
            IntrospectionSupport.setProperty(context.getTypeConverter(), json, "unmarshalType", classType);
            IntrospectionSupport.setProperty(context.getTypeConverter(), json, "useList", useList);
        }
        context.addService(json);

        DataFormat outJson = context.resolveDataFormat(name);
        if (outClassType == null && outType != null) {
            outClassType = context.getClassResolver().resolveMandatoryClass(outType);
        }
        if (outClassType == null && outTypeList != null) {
            outClassType = context.getClassResolver().resolveMandatoryClass(outTypeList);
        }
        if (outClassType != null) {
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "unmarshalType", outClassType);
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJson, "useList", outUseList);
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
        if (classType == null && type != null) {
            classType = context.getClassResolver().resolveMandatoryClass(type);
        }
        if (classType == null && typeList != null) {
            classType = context.getClassResolver().resolveMandatoryClass(type);
        }
        if (classType != null) {
            JAXBContext jc = JAXBContext.newInstance(classType);
            IntrospectionSupport.setProperty(context.getTypeConverter(), jaxb, "context", jc);
        }
        context.addService(jaxb);

        DataFormat outJaxb = context.resolveDataFormat(name);
        if (outClassType == null && outType != null) {
            outClassType = context.getClassResolver().resolveMandatoryClass(outType);
        }
        if (outClassType == null && outTypeList != null) {
            outClassType = context.getClassResolver().resolveMandatoryClass(outTypeList);
        }
        if (outClassType != null) {
            JAXBContext jc = JAXBContext.newInstance(outClassType);
            IntrospectionSupport.setProperty(context.getTypeConverter(), outJaxb, "context", jc);
        } else if (classType != null) {
            // fallback and use the context from the input
            JAXBContext jc = JAXBContext.newInstance(classType);
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
        this.useList = false;
    }

    public String getTypeList() {
        return typeList;
    }

    public void setTypeList(String typeList) {
        this.typeList = typeList;
        this.useList = true;
    }

    public Class<?> getClassType() {
        return classType;
    }

    public void setClassType(Class<?> classType) {
        this.classType = classType;
    }

    public boolean isUseList() {
        return useList;
    }

    public void setUseList(boolean useList) {
        this.useList = useList;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String type) {
        this.outType = type;
        this.outUseList = false;
    }

    public String getOutTypeList() {
        return outTypeList;
    }

    public void setOutTypeList(String typeList) {
        this.outTypeList = typeList;
        this.outUseList = true;
    }

    public Class<?> getOutClassType() {
        return outClassType;
    }

    public void setOutClassType(Class<?> classType) {
        this.outClassType = classType;
    }

    public boolean isOutUseList() {
        return outUseList;
    }

    public void setOutUseList(boolean useList) {
        this.outUseList = useList;
    }

}
