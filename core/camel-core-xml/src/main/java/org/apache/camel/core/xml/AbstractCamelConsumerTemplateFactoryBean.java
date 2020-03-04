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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.impl.engine.DefaultConsumerTemplate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A factory for creating a new {@link org.apache.camel.ConsumerTemplate}
 * instance with a minimum of XML
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelConsumerTemplateFactoryBean extends AbstractCamelFactoryBean<ConsumerTemplate> {

    @XmlTransient
    private ConsumerTemplate template;
    @XmlAttribute @Metadata(description = "Sets a custom maximum cache size to use in the backing cache pools.")
    private Integer maximumCacheSize;

    @Override
    public ConsumerTemplate getObject() throws Exception {
        template = new DefaultConsumerTemplate(getCamelContext());

        // set custom cache size if provided
        if (maximumCacheSize != null) {
            template.setMaximumCacheSize(maximumCacheSize);
        }

        // must start it so its ready to use
        ServiceHelper.startService(template);
        return template;
    }

    @Override
    public Class<ConsumerTemplate> getObjectType() {
        return ConsumerTemplate.class;
    }

    @Override
    public void destroy() throws Exception {
        ServiceHelper.stopService(template);
    }

    // Properties
    // -------------------------------------------------------------------------

    public Integer getMaximumCacheSize() {
        return maximumCacheSize;
    }

    public void setMaximumCacheSize(Integer maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }
}
