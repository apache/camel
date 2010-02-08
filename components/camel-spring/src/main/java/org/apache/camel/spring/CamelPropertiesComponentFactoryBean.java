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
package org.apache.camel.spring;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesResolver;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A Spring {@link org.springframework.beans.factory.FactoryBean} for creating a new
 * {@link org.apache.camel.component.properties.PropertiesComponent} instance with a minimum of XML
 *
 * @version $Revision$
 */
@XmlRootElement(name = "propertyPlaceholder")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelPropertiesComponentFactoryBean extends IdentifiedType implements FactoryBean, InitializingBean, CamelContextAware, ApplicationContextAware {
    @XmlAttribute(required = true)
    private String location;
    @XmlAttribute
    private String propertiesResolverRef;
    @XmlAttribute
    private String camelContextId;
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;

    public void afterPropertiesSet() throws Exception {
        if (camelContext == null && camelContextId != null) {
            camelContext = CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
        }
        if (camelContext == null) {
            throw new IllegalArgumentException("A CamelContext or a CamelContextId must be injected!");
        }
    }

    public Object getObject() throws Exception {
        CamelContext context = getCamelContext();
        if (context.hasComponent(getId()) != null) {
            throw new IllegalArgumentException("A component already exists with the name " + getId());
        }

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation(getLocation());

        // if using a custom resolver
        if (ObjectHelper.isNotEmpty(getPropertiesResolverRef())) {
            PropertiesResolver resolver = CamelContextHelper.mandatoryLookup(getCamelContext(), getPropertiesResolverRef(),
                                                                             PropertiesResolver.class);
            pc.setPropertiesResolver(resolver);
        }

        return pc;
    }

    public Class getObjectType() {
        return PropertiesComponent.class;
    }

    public boolean isSingleton() {
        return true;
    }

    // Properties
    // -------------------------------------------------------------------------
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void setCamelContextId(String camelContextId) {
        this.camelContextId = camelContextId;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPropertiesResolverRef() {
        return propertiesResolverRef;
    }

    public void setPropertiesResolverRef(String propertiesResolverRef) {
        this.propertiesResolverRef = propertiesResolverRef;
    }
}