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
package org.apache.camel.model;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.Required;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for sending to an endpoint with an optional {@link ExchangePattern}
 *
 * @version $Revision$
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class SendDefinition<Type extends ProcessorDefinition<Type>> extends ProcessorDefinition<Type> {
    @XmlAttribute(required = false)
    protected String uri;
    @XmlAttribute(required = false)
    protected String ref;
    @XmlTransient
    protected Endpoint endpoint;

    public SendDefinition() {
    }

    public SendDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Endpoint endpoint = resolveEndpoint(routeContext);
        return new SendProcessor(endpoint, getPattern());
    }

    public Endpoint resolveEndpoint(RouteContext context) {
        if (endpoint == null) {
            return context.resolveEndpoint(getUri(), getRef());
        } else {
            return endpoint;
        }
    }

    // Properties
    // -----------------------------------------------------------------------
    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getUri() {
        return uri;
    }

    @Required
    public void setUri(String uri) {
        this.uri = uri;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public ExchangePattern getPattern() {
        return null;
    }

    public List<ProcessorDefinition> getOutputs() {
        return Collections.emptyList();
    }

    /**
     * Returns the endpoint URI or the name of the reference to it
     */
    public Object getUriOrRef() {
        String uri = getUri();
        if (ObjectHelper.isNotEmpty(uri)) {
            return uri;
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        }
        return getRef();
    }

    @Override
    public String getLabel() {
        return FromDefinition.description(getUri(), getRef(), getEndpoint());
    }
}
