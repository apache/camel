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
import java.util.Map;
import javax.xml.transform.TransformerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher;
import org.apache.camel.component.spring.ws.bean.CamelEndpointMapping;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.component.spring.ws.type.EndpointMappingType;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;

/**
 * Apache Camel component for working with Spring Web Services (a.k.a Spring-WS).
 */
public class SpringWebserviceComponent extends DefaultComponent {
    private static final Log LOG = LogFactory.getLog(SpringWebserviceComponent.class);

    public SpringWebserviceComponent() {
        super();
    }

    public SpringWebserviceComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringWebserviceConfiguration configuration = new SpringWebserviceConfiguration();
        addConsumerConfiguration(remaining, parameters, configuration);
        addProducerConfiguration(remaining, parameters, configuration);
        addXmlConverterToConfiguration(parameters, configuration);
        setProperties(configuration, parameters);
        return new SpringWebserviceEndpoint(this, configuration);
    }

    private void addConsumerConfiguration(String remaining, Map<String, Object> parameters,
                                          SpringWebserviceConfiguration configuration) {
        EndpointMappingType type = EndpointMappingType.getTypeFromUriPrefix(remaining);
        if (type != null) {
            LOG.debug("Building Spring Web Services consumer of type " + type);
            String lookupKey = getLookupKey(remaining, type);
            if (EndpointMappingType.BEANNAME.equals(type)) {
                addEndpointDispatcherToConfiguration(configuration, lookupKey);
            } else {
                addEndpointMappingToConfiguration(parameters, configuration);
            }
            if (EndpointMappingType.XPATHRESULT.equals(type)) {
                XPathExpression expression = getXPathExpressionFromParameters(parameters);
                configuration.setEndpointMappingKey(new EndpointMappingKey(type, lookupKey, expression));
            } else {
                configuration.setEndpointMappingKey(new EndpointMappingKey(type, lookupKey, null));
            }
        }
    }

    private void addProducerConfiguration(String remaining, Map<String, Object> parameters,
                                          SpringWebserviceConfiguration configuration) throws URISyntaxException {
        if (configuration.getEndpointMapping() == null && configuration.getEndpointDispatcher() == null) {
            LOG.debug("Building Spring Web Services producer");
            URI webServiceEndpointUri = new URI(UnsafeUriCharactersEncoder.encode(remaining));

            // Obtain a WebServiceTemplate from the registry when specified by
            // an option on the component, else create a new template with
            // Spring-WS defaults
            WebServiceTemplate webServiceTemplate = resolveAndRemoveReferenceParameter(parameters,
                    "webServiceTemplate", WebServiceTemplate.class, new WebServiceTemplate());
            WebServiceMessageSender messageSender = resolveAndRemoveReferenceParameter(parameters,
                    "messageSender", WebServiceMessageSender.class, null);
            WebServiceMessageFactory messageFactory = resolveAndRemoveReferenceParameter(parameters,
                    "messageFactory", WebServiceMessageFactory.class, null);

            if (webServiceTemplate.getDefaultUri() == null) {
                webServiceTemplate.setDefaultUri(webServiceEndpointUri.toString());
            }
            if (messageSender != null) {
                webServiceTemplate.setMessageSender(messageSender);
            }
            if (messageFactory != null) {
                webServiceTemplate.setMessageFactory(messageFactory);
            }
            configuration.setWebServiceTemplate(webServiceTemplate);
        }
    }

    private String getLookupKey(String remaining, EndpointMappingType type) {
        String lookupKey = remaining.substring(type.getPrefix().length());
        return lookupKey.startsWith("//") ? lookupKey.substring(2) : lookupKey;
    }

    private XPathExpression getXPathExpressionFromParameters(Map<String, Object> parameters) {
        String xpathExpression = getAndRemoveParameter(parameters, "expression", String.class);
        if (xpathExpression == null) {
            throw new RuntimeCamelException("Expression parameter is required when using XPath endpoint mapping");
        }
        XPathExpression expression = XPathExpressionFactory.createXPathExpression(xpathExpression);
        return expression;
    }

    private void addEndpointMappingToConfiguration(Map<String, Object> parameters,
                                                   SpringWebserviceConfiguration configuration) {
        // Obtain generic CamelEndpointMapping from registry
        CamelEndpointMapping endpointMapping = resolveAndRemoveReferenceParameter(parameters, "endpointMapping", CamelEndpointMapping.class, null);
        if (endpointMapping == null && configuration.getEndpointDispatcher() == null) {
            throw new IllegalArgumentException("No CamelEndpointMapping found in Spring ApplicationContext."
                    + " This bean is required for Spring-WS consumer support (unless the 'springws:beanname:' URI scheme is used)");
        }
        configuration.setEndpointMapping(endpointMapping);
    }

    private void addEndpointDispatcherToConfiguration(SpringWebserviceConfiguration configuration, String lookupKey) {
        // Obtain CamelEndpointDispatcher with the given name from registry
        CamelEndpointDispatcher endpoint = CamelContextHelper.mandatoryLookup(getCamelContext(), lookupKey, CamelEndpointDispatcher.class);
        configuration.setEndpointDispatcher(endpoint);
    }

    private void addXmlConverterToConfiguration(Map<String, Object> parameters, SpringWebserviceConfiguration configuration) {
        XmlConverter xmlConverter = new XmlConverter();
        TransformerFactory transformerFactory = resolveAndRemoveReferenceParameter(parameters, "transformerFactory", TransformerFactory.class, null);
        if (transformerFactory != null) {
            xmlConverter.setTransformerFactory(transformerFactory);
        }
        configuration.setXmlConverter(xmlConverter);
    }
}
