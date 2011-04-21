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

import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;

import static org.apache.camel.util.ObjectHelper.notNull;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelEndpointFactoryBean extends AbstractCamelFactoryBean<Endpoint> {
    @SuppressWarnings("unused")
    @XmlAttribute(required = false)
    @Deprecated
    private Boolean singleton;
    @XmlAttribute
    private String uri;
    @XmlTransient
    private Endpoint endpoint;

    public Endpoint getObject() throws Exception {
        if (endpoint == null || !endpoint.isSingleton()) {
            notNull(uri, "uri");
            endpoint = getCamelContext().getEndpoint(uri);
            if (endpoint == null) {
                throw new NoSuchEndpointException(uri);
            }
        }
        return endpoint;
    }

    public Class<Endpoint> getObjectType() {
        return Endpoint.class;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI to use to resolve the endpoint
     *
     * @param uri the URI used to set the endpoint
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

}
