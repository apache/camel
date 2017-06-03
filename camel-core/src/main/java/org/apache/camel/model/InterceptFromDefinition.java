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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.ExpressionAdapter;

/**
 * Intercepts incoming messages
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "interceptFrom")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptFromDefinition extends InterceptDefinition {
    @XmlAttribute
    protected String uri;

    public InterceptFromDefinition() {
    }

    public InterceptFromDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return "InterceptFrom[" + getOutputs() + "]";
    }

    @Override
    public String getLabel() {
        return "interceptFrom";
    }

    @Override
    public boolean isAbstract() {
        return true;
    }

    @Override
    public boolean isTopLevelOnly() {
        return true;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // insert a set header definition so we can set the intercepted endpoint uri as a header
        // this allows us to use the same header for both the interceptFrom and interceptSendToEndpoint
        SetHeaderDefinition headerDefinition = new SetHeaderDefinition(Exchange.INTERCEPTED_ENDPOINT, new ExpressionAdapter() {
            public Object evaluate(Exchange exchange, Class type) {
                if (exchange.getFromEndpoint() != null) {
                    return exchange.getFromEndpoint().getEndpointUri();
                } else {
                    return null;
                }
            }

            public String toString() {
                return "";
            }
        });
        getOutputs().add(0, headerDefinition);

        return this.createChildProcessor(routeContext, true);
    }

    public String getUri() {
        return uri;
    }

    /**
     * Intercept incoming messages from the uri or uri pattern.
     * If this option is not configured, then all incoming messages is intercepted.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }
}
