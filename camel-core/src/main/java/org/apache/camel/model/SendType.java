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
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for sending to an endpoint with an optional {@link ExchangePattern}
 *
 * @version $Revision$
 */
//@XmlType(name = "sendType")
@XmlAccessorType(XmlAccessType.FIELD)
public class SendType<Type extends ProcessorType> extends ProcessorType<Type> {
    @XmlAttribute(required = false)
    private String uri;
    @XmlAttribute(required = false)
    private String ref;
    @XmlTransient
    private Endpoint endpoint;

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Endpoint endpoint = resolveEndpoint(routeContext);
        return new SendProcessor(endpoint, getPattern());
    }

    public Endpoint resolveEndpoint(RouteContext context) {
        if (endpoint == null) {
            endpoint = context.resolveEndpoint(getUri(), getRef());
        }
        return endpoint;
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

    public List<ProcessorType<?>> getOutputs() {
        return Collections.EMPTY_LIST;
    }

    /**
     * Returns the endpoint URI or the name of the reference to it
     */
    public Object getUriOrRef() {
        String uri = getUri();
        if (ObjectHelper.isEmpty(uri)) {
            return uri;
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        }
        return getRef();
    }

    @Override
    public String getLabel() {
        return FromType.description(getUri(), getRef(), getEndpoint());
    }
}
