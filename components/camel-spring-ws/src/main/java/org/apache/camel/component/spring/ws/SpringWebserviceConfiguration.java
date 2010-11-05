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
package org.apache.camel.component.spring.ws;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher;
import org.apache.camel.component.spring.ws.bean.CamelEndpointMapping;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.springframework.ws.client.core.WebServiceTemplate;

public class SpringWebserviceConfiguration {

    /* Producer configuration */
    private WebServiceTemplate webServiceTemplate;
    private String soapAction;
    private URI wsAddressingAction;

    /* Consumer configuration */
    private CamelEndpointMapping endpointMapping;
    private CamelEndpointDispatcher endpointDispatcher;
    private EndpointMappingKey endpointMappingKey;

    private XmlConverter xmlConverter;

    public WebServiceTemplate getWebServiceTemplate() {
        return webServiceTemplate;
    }

    public void setWebServiceTemplate(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }

    public String getEndpointUri() {
        if (endpointMappingKey != null) {
            // only for consumers, use lookup key as endpoint uri/key
            return endpointMappingKey.getLookupKey();
        } else if (webServiceTemplate != null) {
            return webServiceTemplate.getDefaultUri();
        }
        return null;
    }

    public URI getWsAddressingAction() {
        return wsAddressingAction;
    }

    public void setWsAddressingAction(URI wsAddressingAction) {
        this.wsAddressingAction = wsAddressingAction;
    }

    public void setWsAddressingAction(String wsAddressingAction) throws URISyntaxException {
        this.wsAddressingAction = new URI(wsAddressingAction);
    }

    public CamelEndpointMapping getEndpointMapping() {
        return endpointMapping;
    }

    public void setEndpointMapping(CamelEndpointMapping endpointMapping) {
        this.endpointMapping = endpointMapping;
    }

    public EndpointMappingKey getEndpointMappingKey() {
        return endpointMappingKey;
    }

    public void setEndpointMappingKey(EndpointMappingKey endpointMappingKey) {
        this.endpointMappingKey = endpointMappingKey;
    }

    public CamelEndpointDispatcher getEndpointDispatcher() {
        return endpointDispatcher;
    }

    public void setEndpointDispatcher(CamelEndpointDispatcher endpointDispatcher) {
        this.endpointDispatcher = endpointDispatcher;
    }

    public XmlConverter getXmlConverter() {
        return xmlConverter;
    }

    public void setXmlConverter(XmlConverter xmlConverter) {
        this.xmlConverter = xmlConverter;
    }
}
