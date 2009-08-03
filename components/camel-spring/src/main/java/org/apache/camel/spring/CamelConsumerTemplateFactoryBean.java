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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.DefaultConsumerTemplate;
import org.apache.camel.model.IdentifiedType;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * A Spring {@link org.springframework.beans.factory.FactoryBean} for creating a new {@link org.apache.camel.ConsumerTemplate}
 * instance with a minimum of XML
 *
 * @version $Revision$
 */
@XmlRootElement(name = "consumerTemplate")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelConsumerTemplateFactoryBean extends IdentifiedType implements FactoryBean, InitializingBean, CamelContextAware {
    @XmlTransient
    private CamelContext camelContext;

    public void afterPropertiesSet() throws Exception {
        if (camelContext == null) {
            throw new IllegalArgumentException("A CamelContext must be injected!");
        }
    }

    public Object getObject() throws Exception {
        return new DefaultConsumerTemplate(getCamelContext());
    }

    public Class getObjectType() {
        return DefaultConsumerTemplate.class;
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

}