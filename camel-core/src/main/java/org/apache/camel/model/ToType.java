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
package org.apache.camel.model;

import org.apache.camel.Processor;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.SendProcessor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.List;

/**
 * Represents an XML &lt;to/&gt; element
 *
 * @version $Revision: $
 */
@XmlRootElement(name = "to")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToType extends ProcessorType {
    @XmlAttribute
    private String uri;
    @XmlAttribute
    private String ref;
    @XmlElement(required = false)
    private List<InterceptorRef> interceptors;

    @Override
    public String toString() {
        return "To[" + FromType.description(getUri(), getRef()) + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        Endpoint endpoint = resolveEndpoint(routeContext);
        return new SendProcessor(endpoint);
    }

    public Endpoint resolveEndpoint(RouteContext context) {
        return context.resolveEndpoint(getUri(), getRef());
    }

    // Properties
    //-----------------------------------------------------------------------
    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI of the endpoint to use
     *
     * @param uri the endpoint URI to use
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getRef() {
        return ref;
    }

    /**
     * Sets the name of the endpoint within the registry (such as the Spring ApplicationContext or JNDI) to use
     *
     * @param ref the reference name to use
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public List<ProcessorType> getOutputs() {
        return Collections.EMPTY_LIST;
    }

    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }
}