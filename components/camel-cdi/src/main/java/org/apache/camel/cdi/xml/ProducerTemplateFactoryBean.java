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
package org.apache.camel.cdi.xml;

import javax.enterprise.inject.spi.BeanManager;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.core.xml.AbstractCamelProducerTemplateFactoryBean;

/**
 * A factory for creating a new {@link org.apache.camel.ProducerTemplate}
 * instance with a minimum of XML
 */
@XmlRootElement(name = "template")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProducerTemplateFactoryBean extends AbstractCamelProducerTemplateFactoryBean implements BeanManagerAware {

    @XmlTransient
    private BeanManager manager;

    @Override
    public void setBeanManager(BeanManager manager) {
        this.manager = manager;
    }

    @Override
    protected CamelContext getCamelContextWithId(String camelContextId) {
        return BeanManagerHelper.getCamelContextById(manager, camelContextId);
    }

    @Override
    protected CamelContext discoverDefaultCamelContext() {
        return BeanManagerHelper.getDefaultCamelContext(manager);
    }

    @Override
    @SuppressWarnings("unchecked")
    // Work-around as DefaultProducerTemplate does not have a default no-args constructor
    // which leads to OpenWebBeans being unable to create proxies for the corresponding bean
    public Class getObjectType() {
        return ProducerTemplate.class;
    }
}
