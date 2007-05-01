/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.camel.component.jbi;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.jbi.util.IntrospectionSupport;
import org.apache.servicemix.jbi.util.URISupport;
import org.apache.servicemix.jbi.resolver.URIResolver;

import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Deploys the camel endpoints within JBI
 *
 * @version $Revision: 426415 $
 */
public class CamelJbiComponent extends DefaultComponent implements Component<Exchange> {
    private JbiBinding binding;
    private CamelContext camelContext;
    private ScheduledExecutorService executorService;

    /**
     * @return List of endpoints
     * @see org.apache.servicemix.common.DefaultComponent#getConfiguredEndpoints()
     */
    @Override
    protected List<CamelJbiEndpoint> getConfiguredEndpoints() {
        // TODO need to register to the context for new endpoints...
        List<CamelJbiEndpoint> answer = new ArrayList<CamelJbiEndpoint>();
//        Collection<Endpoint> endpoints = camelContext.getEndpoints();
//        for (Endpoint endpoint : endpoints) {
//          answer.add(createJbiEndpoint(endpoint));
//        }
        return answer;
    }

    /**
     * @return Class[]
     * @see org.apache.servicemix.common.DefaultComponent#getEndpointClasses()
     */
    @Override
    protected Class[] getEndpointClasses() {
        return new Class[]{CamelJbiEndpoint.class};
    }


    /**
     * @return the binding
     */
    public JbiBinding getBinding() {
        if (binding == null) {
            binding = new JbiBinding();
        }
        return binding;
    }

    /**
     * @param binding the binding to set
     */
    public void setBinding(JbiBinding binding) {
        this.binding = binding;
    }

    @Override
    protected String[] getEPRProtocols() {
        return new String[]{"camel"};
    }

    protected org.apache.servicemix.common.Endpoint getResolvedEPR(ServiceEndpoint ep) throws Exception {
        CamelJbiEndpoint endpoint = createEndpoint(ep);
        endpoint.activate();
        return endpoint;
    }

    public CamelJbiEndpoint createEndpoint(ServiceEndpoint ep) throws URISyntaxException {
        URI uri = new URI(ep.getEndpointName());
        Map map = URISupport.parseQuery(uri.getQuery());
        String camelUri = uri.getSchemeSpecificPart();
        Endpoint camelEndpoint = getCamelContext().getEndpoint(camelUri);
        Processor processor = null;
        try {
            processor = camelEndpoint.createProducer();
        }
        catch (Exception e) {
            throw new FailedToCreateProducerException(camelEndpoint, e);
        }
        CamelJbiEndpoint endpoint = new CamelJbiEndpoint(getServiceUnit(), camelEndpoint, getBinding(), processor);

        IntrospectionSupport.setProperties(endpoint, map);

        // TODO
        //endpoint.setRole(MessageExchange.Role.PROVIDER);

        return endpoint;
    }

    // Resolve Camel Endpoints
    //-------------------------------------------------------------------------
    public Endpoint<Exchange> createEndpoint(String uri) {
        if (uri.startsWith("jbi:")) {
            uri = uri.substring("jbi:".length());

            return new JbiEndpoint(this, uri);
        }
        return null;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ScheduledExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = new ScheduledThreadPoolExecutor(5);
        }
        return executorService;
    }

    /**
     * Returns a JBI endpoint created for the given Camel endpoint
     */
    public CamelJbiEndpoint activateJbiEndpoint(JbiEndpoint camelEndpoint, Processor processor) throws Exception {
        CamelJbiEndpoint jbiEndpoint;
        String endpointUri = camelEndpoint.getEndpointUri();
        if (endpointUri.startsWith("endpoint:")) {
            // lets decode "service:serviceNamespace:serviceName:endpointName
            String uri = endpointUri.substring("endpoint:".length());
            String[] parts = new String[0];
            try {
                parts = URIResolver.split3(uri);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Expected syntax endpoint:[serviceNamespace]:[serviceName]:[endpointName] but was given: " + endpointUri + ". Cause: " + e, e);
            }
            QName service = new QName(parts[0], parts[1]);
            String endpoint = parts[2];
            jbiEndpoint = new CamelJbiEndpoint(getServiceUnit(), service, endpoint, camelEndpoint, getBinding(), processor);
        }
        else {
            jbiEndpoint = new CamelJbiEndpoint(getServiceUnit(), camelEndpoint, getBinding(), processor);
        }

        // the following method will activate the new dynamic JBI endpoint
        addEndpoint(jbiEndpoint);
        return jbiEndpoint;
    }
}
