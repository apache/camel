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
import org.apache.camel.CamelContextAware;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelFactoryBean<T> extends IdentifiedType implements CamelContextAware {

    @XmlAttribute @Metadata(description = "Id of CamelContext to use if there are multiple CamelContexts in the same JVM")
    private String camelContextId;
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private Boolean customId;

    public abstract T getObject() throws Exception;

    protected abstract CamelContext getCamelContextWithId(String camelContextId);

    /**
     * If no explicit camelContext or camelContextId has been set
     * then try to discover a default {@link CamelContext} to use.
     */
    protected CamelContext discoverDefaultCamelContext() {
        return null;
    }

    public void afterPropertiesSet() throws Exception {
        // Always try to resolved the camel context by using the camelContextId
        if (ObjectHelper.isNotEmpty(camelContextId)) {
            camelContext = getCamelContextWithId(camelContextId);
            if (camelContext == null) {
                throw new IllegalStateException("Cannot find CamelContext with id: " + camelContextId);
            }
        }
        if (camelContext == null) {
            camelContext = discoverDefaultCamelContext();
        }
    }

    public void destroy() throws Exception {
    }

    public CamelContext getCamelContext() {
        if (ObjectHelper.isNotEmpty(camelContextId)) {
            // always return the context by its id
            return getCamelContextWithId(camelContextId);
        }
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

    public Boolean getCustomId() {
        return customId;
    }

    public void setCustomId(Boolean customId) {
        this.customId = customId;
    }

    public boolean isSingleton() {
        return true;
    }

    public abstract Class<? extends T> getObjectType();

}
