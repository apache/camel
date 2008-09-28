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
package org.apache.camel.rest.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;

/**
 * @version $Revision$
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Endpoints {
    @XmlElement(name = "endpoint")
    private List<EndpointLink> endpoints = new ArrayList<EndpointLink>();

    public Endpoints() {
    }

    public Endpoints(CamelContext camelContext) {
        this();
        load(camelContext);
    }

    @Override
    public String toString() {
        return "Endpoints" + endpoints;

    }

    public List<EndpointLink> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointLink> endpoints) {
        this.endpoints = endpoints;
    }

    public void load(CamelContext camelContext) {
        Collection<Endpoint> endpoints = camelContext.getSingletonEndpoints();
        for (Endpoint endpoint : endpoints) {
            addEndpoint(createEndpointLink(endpoint));
        }

    }

    protected EndpointLink createEndpointLink(Endpoint endpoint) {
        EndpointLink answer = new EndpointLink();
        answer.load(endpoint);
        return answer;
    }

    public void addEndpoint(EndpointLink endpointLink) {
        getEndpoints().add(endpointLink);
    }
}
