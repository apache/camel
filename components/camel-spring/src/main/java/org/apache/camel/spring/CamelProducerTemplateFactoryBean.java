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
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A Spring {@link FactoryBean} for creating a new {@link org.apache.camel.ProducerTemplate}
 * instance with a minimum of XML
 * 
 * @version $Revision$
 */
@XmlRootElement(name = "template")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelProducerTemplateFactoryBean extends IdentifiedType implements FactoryBean, InitializingBean, CamelContextAware, ApplicationContextAware {
    @XmlAttribute(required = false)
    private String defaultEndpoint;
    @XmlAttribute
    private String camelContextId;
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;
    @XmlAttribute
    private Integer maximumCacheSize;

    public void afterPropertiesSet() throws Exception {
        if (camelContext == null && camelContextId != null) {
            camelContext = CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
        }
        if (camelContext == null) {
            throw new IllegalArgumentException("A CamelContext or a CamelContextId must be injected!");
        }
    }

    public Object getObject() throws Exception {
        ProducerTemplate answer;

        CamelContext context = getCamelContext();
        if (defaultEndpoint != null) {
            Endpoint endpoint = context.getEndpoint(defaultEndpoint);
            if (endpoint == null) {
                throw new IllegalArgumentException("No endpoint found for URI: " + defaultEndpoint);
            } else {
                answer = new DefaultProducerTemplate(context, endpoint);
            }
        } else {
            answer = new DefaultProducerTemplate(context);
        }

        // set custom cache size if provided
        if (maximumCacheSize != null) {
            answer.setMaximumCacheSize(maximumCacheSize);
        }

        // must start it so its ready to use
        answer.start();
        return answer;
    }

    public Class getObjectType() {
        return DefaultProducerTemplate.class;
    }

    public boolean isSingleton() {
        return false;
    }

    // Properties
    // -------------------------------------------------------------------------
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Sets the default endpoint URI used by default for sending message exchanges
     */
    public void setDefaultEndpoint(String defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }

    public void setCamelContextId(String camelContextId) {
        this.camelContextId = camelContextId;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public Integer getMaximumCacheSize() {
        return maximumCacheSize;
    }

    public void setMaximumCacheSize(Integer maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }
}
