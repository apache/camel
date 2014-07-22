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

@XmlRootElement(name = "restBinding")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestBindingDefinition extends NoOutputDefinition {

    @XmlAttribute
    private String jsonDataFormat;

    @XmlAttribute
    private String xmlDataFormat;

    @XmlAttribute
    private String classType;

    @XmlTransient
    private Class<?> resolvedClassType;

    @Override
    public String toString() {
        return "RestBinding";
    }

    @Override
    public String getShortName() {
        return "rest";
    }

    // TODO: allow to configure if json/jaxb is mandatory, or optional

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        CamelContext context = routeContext.getCamelContext();

        // setup json data format
        String name = jsonDataFormat;
        if (name == null) {
            name = "json-jackson";
        }
        DataFormat json = context.resolveDataFormat(name);
        if (json == null) {
            throw new IllegalArgumentException("DataFormat " + name + " not found.");
        }
        if (resolvedClassType == null && classType != null) {
            resolvedClassType = context.getClassResolver().resolveMandatoryClass(classType);
        }
        if (resolvedClassType != null) {
            IntrospectionSupport.setProperty(context.getTypeConverter(), json, "unmarshalType", resolvedClassType);
        }
        context.addService(json);

        // setup xml data format
        name = xmlDataFormat;
        if (name == null) {
            name = "jaxb";
        }
        DataFormat jaxb = context.resolveDataFormat(name);
        if (jaxb == null) {
            throw new IllegalArgumentException("DataFormat " + name + " not found.");
        }
        if (resolvedClassType == null && classType != null) {
            resolvedClassType = context.getClassResolver().resolveMandatoryClass(classType);
        }
        if (resolvedClassType != null) {
            JAXBContext jc = JAXBContext.newInstance(resolvedClassType);
            IntrospectionSupport.setProperty(context.getTypeConverter(), jaxb, "context", jc);
        }
        context.addService(jaxb);

        return new RestBindingProcessor(json, jaxb);
    }

    public String getClassType() {
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public Class<?> getResolvedClassType() {
        return resolvedClassType;
    }

    public void setResolvedClassType(Class<?> resolvedClassType) {
        this.resolvedClassType = resolvedClassType;
    }

}
