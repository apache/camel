/*
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
package org.apache.camel.cdi.xml;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.core.xml.AbstractCamelFactoryBean;

@XmlRootElement(name = "beans", namespace = "http://www.springframework.org/schema/beans")
@XmlAccessorType(XmlAccessType.FIELD)
public class ApplicationContextFactoryBean {

    @XmlElement(name = "camelContext")
    private List<CamelContextFactoryBean> contexts = new ArrayList<>();

    @XmlElement(name = "errorHandler")
    private List<ErrorHandlerDefinition> errorHandlers = new ArrayList<>();

    @XmlElement(name = "import")
    private List<ImportDefinition> imports = new ArrayList<>();

    @XmlElement(name = "restContext")
    private List<RestContextDefinition> restContexts = new ArrayList<>();

    @XmlElement(name = "routeContext")
    private List<RouteContextDefinition> routeContexts = new ArrayList<>();

    @XmlElements({
        @XmlElement(name = "consumerTemplate", type = ConsumerTemplateFactoryBean.class),
        @XmlElement(name = "endpoint", type = EndpointFactoryBean.class),
        @XmlElement(name = "redeliveryPolicyProfile", type = RedeliveryPolicyFactoryBean.class),
        @XmlElement(name = "template", type = ProducerTemplateFactoryBean.class),
        @XmlElement(name = "threadPool", type = ThreadPoolFactoryBean.class)
    })
    private List<AbstractCamelFactoryBean<?>> beans = new ArrayList<>();

    public List<CamelContextFactoryBean> getContexts() {
        return contexts;
    }

    public void setContexts(List<CamelContextFactoryBean> contexts) {
        this.contexts = contexts;
    }

    public List<ErrorHandlerDefinition> getErrorHandlers() {
        return errorHandlers;
    }

    public void setErrorHandlers(List<ErrorHandlerDefinition> errorHandlers) {
        this.errorHandlers = errorHandlers;
    }

    public List<ImportDefinition> getImports() {
        return imports;
    }

    public void setImports(List<ImportDefinition> imports) {
        this.imports = imports;
    }

    public List<RestContextDefinition> getRestContexts() {
        return restContexts;
    }

    public void setRestContexts(List<RestContextDefinition> restContexts) {
        this.restContexts = restContexts;
    }

    public List<RouteContextDefinition> getRouteContexts() {
        return routeContexts;
    }

    public void setRouteContexts(List<RouteContextDefinition> routeContexts) {
        this.routeContexts = routeContexts;
    }

    public List<AbstractCamelFactoryBean<?>> getBeans() {
        return beans;
    }

    public void setBeans(List<AbstractCamelFactoryBean<?>> beans) {
        this.beans = beans;
    }
}
