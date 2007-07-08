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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ProcessorFactory;
import org.apache.camel.impl.EventDrivenConsumerRoute;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents an XML &lt;route/&gt; element
 *
 * @version $Revision: $
 */
@XmlRootElement(name = "root")
public class RouteElement implements CamelContextAware, ProcessorFactory {
    private String uri;
    private Endpoint endpoint;
    private CamelContext camelContext;

    public Route createRoute() throws Exception {
        return new EventDrivenConsumerRoute(getEndpoint(), createProcessor());
    }

    // Properties
    //-----------------------------------------------------------------------
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Endpoint getEndpoint() {
        if (endpoint == null) {
            endpoint = resolveEndpoint();
        }
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }
   
    public Processor createProcessor() throws Exception {
        return null;  // TODO
    }

    protected Endpoint resolveEndpoint() {
        CamelContext context = getCamelContext();
        if (context == null) {
            throw new IllegalArgumentException("No CamelContext has been injected!");
        }
        Endpoint answer = context.getEndpoint(getUri());
        if (answer == null) {
            throw new IllegalArgumentException("No Endpoint found for uri: " + getUri());
        }
        return answer;
    }
}
