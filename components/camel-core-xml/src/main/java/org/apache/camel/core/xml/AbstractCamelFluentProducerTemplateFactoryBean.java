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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.DefaultFluentProducerTemplate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ServiceHelper;

/**
 * A factory for creating a new {@link org.apache.camel.FluentProducerTemplate}
 * instance with a minimum of XML
 *
 * @version
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelFluentProducerTemplateFactoryBean extends AbstractCamelFactoryBean<FluentProducerTemplate> {
    @XmlTransient
    private FluentProducerTemplate template;
    @XmlAttribute @Metadata(description = "Sets the default endpoint URI used by default for sending message exchanges")
    private String defaultEndpoint;
    @XmlAttribute @Metadata(description = "Sets a custom maximum cache size to use in the backing cache pools.")
    private Integer maximumCacheSize;

    public FluentProducerTemplate getObject() throws Exception {
        CamelContext context = getCamelContext();
        if (defaultEndpoint != null) {
            Endpoint endpoint = context.getEndpoint(defaultEndpoint);
            if (endpoint == null) {
                throw new IllegalArgumentException("No endpoint found for URI: " + defaultEndpoint);
            } else {
                template = new DefaultFluentProducerTemplate(context);
                template.setDefaultEndpoint(endpoint);
            }
        } else {
            template = new DefaultFluentProducerTemplate(context);
        }

        // set custom cache size if provided
        if (maximumCacheSize != null) {
            template.setMaximumCacheSize(maximumCacheSize);
        }

        // must start it so its ready to use
        ServiceHelper.startService(template);
        return template;
    }

    public Class<DefaultFluentProducerTemplate> getObjectType() {
        return DefaultFluentProducerTemplate.class;
    }

    public void destroy() throws Exception {
        ServiceHelper.stopService(template);
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }

    /**
     * Sets the default endpoint URI used by default for sending message exchanges
     */
    public void setDefaultEndpoint(String defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }

    public Integer getMaximumCacheSize() {
        return maximumCacheSize;
    }

    /**
     * Sets a custom maximum cache size to use in the backing cache pools.
     *
     * @param maximumCacheSize the custom maximum cache size
     */
    public void setMaximumCacheSize(Integer maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }

}
