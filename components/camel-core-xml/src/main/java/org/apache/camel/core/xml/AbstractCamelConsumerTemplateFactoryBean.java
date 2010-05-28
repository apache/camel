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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.impl.DefaultConsumerTemplate;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.util.ServiceHelper;

/**
 * A factory for creating a new {@link org.apache.camel.ConsumerTemplate}
 * instance with a minimum of XML
 *
 * @version $Revision: 934375 $
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelConsumerTemplateFactoryBean extends IdentifiedType implements CamelContextAware {
    @XmlTransient
    private ConsumerTemplate template;
    @XmlAttribute
    private String camelContextId;
    @XmlTransient
    private CamelContext camelContext;
    @XmlAttribute
    private Integer maximumCacheSize;

    public void afterPropertiesSet() throws Exception {
        if (camelContext == null && camelContextId != null) {
            camelContext = getCamelContextWithId(camelContextId);
        }
        if (camelContext == null) {
            throw new IllegalArgumentException("A CamelContext or a CamelContextId must be injected!");
        }
    }

    protected abstract CamelContext getCamelContextWithId(String camelContextId);

    public Object getObject() throws Exception {
        template = new DefaultConsumerTemplate(camelContext);

        // set custom cache size if provided
        if (maximumCacheSize != null) {
            template.setMaximumCacheSize(maximumCacheSize);
        }

        // must start it so its ready to use
        ServiceHelper.startService(template);
        return template;
    }

    public Class getObjectType() {
        return DefaultConsumerTemplate.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public void destroy() throws Exception {
        ServiceHelper.stopService(template);
    }

    // Properties
    // -------------------------------------------------------------------------

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getCamelContextId() {
        return camelContextId;
    }

    public void setCamelContextId(String camelContextId) {
        this.camelContextId = camelContextId;
    }

    public Integer getMaximumCacheSize() {
        return maximumCacheSize;
    }

    public void setMaximumCacheSize(Integer maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }
}