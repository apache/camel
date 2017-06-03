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
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher;
import org.apache.camel.component.spring.ws.bean.CamelSpringWSEndpointMapping;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.apache.camel.component.spring.ws.filter.impl.BasicMessageFilter;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.component.spring.ws.type.EndpointMappingType;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;

/**
 * Apache Camel component for working with Spring Web Services (a.k.a Spring-WS).
 */
public class SpringWebserviceComponent extends UriEndpointComponent implements SSLContextParametersAware {
    private static final Logger LOG = LoggerFactory.getLogger(SpringWebserviceComponent.class);

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public SpringWebserviceComponent() {
        super(SpringWebserviceEndpoint.class);
    }

    public SpringWebserviceComponent(CamelContext context) {
        super(context, SpringWebserviceEndpoint.class);
    }

    @Deprecated
    protected String preProcessUri(String uri) {
        String[] u = uri.split("\\?");
        return u[0].replaceAll("%7B", "(").replaceAll("%7D", ")") + (u.length > 1 ? "?" + u[1] : "");
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringWebserviceConfiguration configuration = new SpringWebserviceConfiguration();
        addConsumerConfiguration(remaining, parameters, configuration);
        addXmlConverterToConfiguration(parameters, configuration);
        setProperties(configuration, parameters);
        configureProducerConfiguration(remaining, configuration);
        configureMessageFilter(configuration);

        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return new SpringWebserviceEndpoint(this, uri, configuration);
    }

    private void addConsumerConfiguration(String remaining, Map<String, Object> parameters, SpringWebserviceConfiguration configuration) {
        EndpointMappingType type = EndpointMappingType.getTypeFromUriPrefix(remaining);
        if (type != null) {
            LOG.debug("Building Spring Web Services consumer of type " + type);
            String lookupKey = getLookupKey(remaining, type);
            if (EndpointMappingType.BEANNAME.equals(type)) {
                addEndpointDispatcherToConfiguration(configuration, lookupKey);
            } else {
                addEndpointMappingToConfiguration(parameters, configuration);
            }
            XPathExpression xPathExpression = null;
            if (type.equals(EndpointMappingType.XPATHRESULT)) {
                String expression = getAndRemoveParameter(parameters, "expression", String.class);
                configuration.setExpression(expression);
                xPathExpression = createXPathExpression(expression);
            }
            configuration.setEndpointMappingKey(new EndpointMappingKey(type, lookupKey, xPathExpression));
        }
    }

    private void configureProducerConfiguration(String remaining, SpringWebserviceConfiguration configuration) throws URISyntaxException {
        if (configuration.getEndpointMapping() == null && configuration.getEndpointDispatcher() == null) {
            LOG.debug("Building Spring Web Services producer");
            URI webServiceEndpointUri = new URI(UnsafeUriCharactersEncoder.encode(remaining));

            // Obtain a WebServiceTemplate from the registry when specified by
            // an option on the component, else create a new template with
            // Spring-WS defaults
            WebServiceTemplate webServiceTemplate = configuration.getWebServiceTemplate();
            if (webServiceTemplate == null) {
                webServiceTemplate = new WebServiceTemplate();
                configuration.setWebServiceTemplate(webServiceTemplate);
            }

            if (webServiceTemplate.getDefaultUri() == null) {
                String uri = webServiceEndpointUri.toString();
                webServiceTemplate.setDefaultUri(uri);
                configuration.setWebServiceEndpointUri(uri);
            }
            if (configuration.getMessageSender() != null) {
                webServiceTemplate.setMessageSender(configuration.getMessageSender());
            }
            if (configuration.getMessageFactory() != null) {
                webServiceTemplate.setMessageFactory(configuration.getMessageFactory());
            }
        }
    }

    private String getLookupKey(String remaining, EndpointMappingType type) {
        String lookupKey = remaining.substring(type.getPrefix().length());
        lookupKey = lookupKey.startsWith("//") ? lookupKey.substring(2) : lookupKey;
        return SpringWebserviceConfiguration.decode(lookupKey);
    }

    private XPathExpression createXPathExpression(String xpathExpression) {
        if (xpathExpression == null) {
            throw new RuntimeCamelException("Expression parameter is required when using XPath endpoint mapping");
        }
        XPathExpression expression = XPathExpressionFactory.createXPathExpression(xpathExpression);
        return expression;
    }

    private void addEndpointMappingToConfiguration(Map<String, Object> parameters,
                                                   SpringWebserviceConfiguration configuration) {
        // Obtain generic CamelSpringWSEndpointMapping from registry
        CamelSpringWSEndpointMapping endpointMapping = resolveAndRemoveReferenceParameter(parameters, "endpointMapping", CamelSpringWSEndpointMapping.class, null);
        if (endpointMapping == null && configuration.getEndpointDispatcher() == null) {
            throw new IllegalArgumentException("No instance of CamelSpringWSEndpointMapping found in Spring ApplicationContext."
                                               + " This bean is required for Spring-WS consumer support (unless the 'spring-ws:beanname:' URI scheme is used)");
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

    /**
     * Configures the messageFilter's factory. The factory is looked up in the endpoint's URI and then in the Spring's context.
     * The bean search mechanism looks for a bean with the name messageFilter.
     * The endpoint's URI search mechanism looks for the URI's key parameter name messageFilter, for instance like this:
     * spring-ws:http://yourdomain.com?messageFilter=<beanName>
     */
    private void configureMessageFilter(SpringWebserviceConfiguration configuration) {
        if (configuration.getMessageFilter() == null) {
            // try to lookup a global filter to use
            final MessageFilter globalMessageFilter = EndpointHelper.resolveReferenceParameter(getCamelContext(), "messageFilter", MessageFilter.class, false);
            if (globalMessageFilter != null) {
                configuration.setMessageFilter(globalMessageFilter);
            } else {
                // use basic as fallback
                configuration.setMessageFilter(new BasicMessageFilter());
            }
        }
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

}
