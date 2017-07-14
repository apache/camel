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
package org.apache.camel.component.spring.ws.type;

import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.springframework.xml.xpath.XPathExpression;

@UriParams
public class EndpointMappingKey {
    @UriPath(label = "consumer")
    private EndpointMappingType type;
    @UriPath(label = "consumer")
    private String lookupKey;

    /* expression in case type is 'xpath' */
    private XPathExpression expression;

    public EndpointMappingKey(EndpointMappingType type, String lookupKey, XPathExpression expression) {
        this.type = type;
        this.lookupKey = lookupKey;
        this.expression = expression;
    }

    public EndpointMappingType getType() {
        return type;
    }

    /**
     * Endpoint mapping type if endpoint mapping is used.
     * <ul>
     *     <li>rootqname - Offers the option to map web service requests based on the qualified name of the root element contained in the message.</li>
     *     <li>soapaction - Used to map web service requests based on the SOAP action specified in the header of the message.</li>
     *     <li>uri - In order to map web service requests that target a specific URI.</li>
     *     <li>xpathresult - Used to map web service requests based on the evaluation of an XPath expression against the incoming message.
     *                       The result of the evaluation should match the XPath result specified in the endpoint URI.</li>
     *     <li>beanname - Allows you to reference an org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher object in order to integrate with
     *                    existing (legacy) endpoint mappings like PayloadRootQNameEndpointMapping, SoapActionEndpointMapping, etc</li>
     * </ul>
     */
    public void setType(EndpointMappingType type) {
        this.type = type;
    }

    public String getLookupKey() {
        return lookupKey;
    }

    /**
     * Endpoint mapping key if endpoint mapping is used
     */
    public void setLookupKey(String lookupKey) {
        this.lookupKey = lookupKey;
    }

    public XPathExpression getExpression() {
        return expression;
    }

    public void setExpression(XPathExpression expression) {
        this.expression = expression;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((lookupKey == null) ? 0 : lookupKey.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        EndpointMappingKey other = (EndpointMappingKey) obj;
        if (lookupKey == null) {
            if (other.lookupKey != null) {
                return false;
            }
        } else if (!lookupKey.equals(other.lookupKey)) {
            return false;
        }
        return true;
    }
}