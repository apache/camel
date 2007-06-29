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
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.servicemix.common.BaseServiceUnitManager;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.Deployer;
import org.apache.servicemix.id.IdGenerator;
import org.apache.servicemix.jbi.resolver.URIResolver;
import org.apache.servicemix.jbi.util.IntrospectionSupport;
import org.apache.servicemix.jbi.util.URISupport;

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
    private IdGenerator idGenerator;
    protected CamelSpringDeployer deployer;

    /* (non-Javadoc)
    * @see org.servicemix.common.BaseComponent#createServiceUnitManager()
    */
    public BaseServiceUnitManager createServiceUnitManager() {
        Deployer[] deployers = new Deployer[]{new CamelSpringDeployer(this)};
        return new BaseServiceUnitManager(this, deployers);
    }

    /**
     * @return List of endpoints
     * @see org.apache.servicemix.common.DefaultComponent#getConfiguredEndpoints()
     */
    @Override
    protected List<CamelJbiEndpoint> getConfiguredEndpoints() {
        List<CamelJbiEndpoint> answer = new ArrayList<CamelJbiEndpoint>();
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
        Processor processor = createCamelProcessor(camelEndpoint);
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
     * Activating a JBI endpoint created by a camel consumer.
     *
     * @returns a JBI endpoint created for the given Camel endpoint
     */
    public CamelJbiEndpoint activateJbiEndpoint(Endpoint camelEndpoint, Processor processor) throws Exception {
        CamelJbiEndpoint jbiEndpoint = createJbiEndpointFromCamel(camelEndpoint, processor);

        // the following method will activate the new dynamic JBI endpoint
        if (deployer != null) {
            // lets add this to the current service unit being deployed
            deployer.addService(jbiEndpoint);
        }
        else {
            addEndpoint(jbiEndpoint);
        }
        return jbiEndpoint;
    }

    public void deactivateJbiEndpoint(CamelJbiEndpoint jbiEndpoint) throws Exception {
        // this will be done by the ServiceUnit
        //jbiEndpoint.deactivate();
    }

    protected CamelJbiEndpoint createJbiEndpointFromCamel(Endpoint camelEndpoint, Processor processor) {
        CamelJbiEndpoint jbiEndpoint;
        String endpointUri = camelEndpoint.getEndpointUri();
        if (camelEndpoint instanceof JbiEndpoint) {
            QName service = null;
            String endpoint = null;
            if (endpointUri.startsWith("name:")) {
                endpoint = endpointUri.substring("name:".length());
                service = CamelJbiEndpoint.SERVICE_NAME;
            }
            else if (endpointUri.startsWith("endpoint:")) {
                String uri = endpointUri.substring("endpoint:".length());
                // lets decode "serviceNamespace sep serviceName sep endpointName
                String[] parts;
                try {
                    parts = URIResolver.split3(uri);
                }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Expected syntax jbi:endpoint:[serviceNamespace][sep][serviceName][sep][endpointName] where sep = '/' or ':' depending on the serviceNamespace, but was given: " + endpointUri + ". Cause: " + e, e);
                }
                service = new QName(parts[0], parts[1]);
                endpoint = parts[2];
            }
            else if (endpointUri.startsWith("service:")) {
                String uri = endpointUri.substring("service:".length());
                // lets decode "serviceNamespace sep serviceName
                String[] parts;
                try {
                    parts = URIResolver.split2(uri);
                }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Expected syntax jbi:endpoint:[serviceNamespace][sep][serviceName] where sep = '/' or ':' depending on the serviceNamespace, but was given: " + endpointUri + ". Cause: " + e, e);
                }
                service = new QName(parts[0], parts[1]);
                endpoint = createEndpointName();
            }
            else {
                throw new IllegalArgumentException("Expected syntax jbi:endpoint:[serviceNamespace][sep][serviceName][sep][endpointName] or  jbi:service:[serviceNamespace][sep][serviceName or jbi:name:[endpointName] but was given: " + endpointUri);
            }
            jbiEndpoint = new CamelJbiEndpoint(getServiceUnit(), service, endpoint, camelEndpoint, getBinding(), processor);
        }
        else {
            jbiEndpoint = new CamelJbiEndpoint(getServiceUnit(), camelEndpoint, getBinding(), processor);
        }
        return jbiEndpoint;
    }

    protected String createEndpointName() {
        if (idGenerator == null) {
            idGenerator = new IdGenerator("camel");
        }
        return idGenerator.generateSanitizedId();
    }

    /**
     * Returns a JBI endpoint created for the given Camel endpoint
     */
    public CamelJbiEndpoint createJbiEndpointFromCamel(Endpoint camelEndpoint) {
        Processor processor = createCamelProcessor(camelEndpoint);
        return createJbiEndpointFromCamel(camelEndpoint, processor);
    }

    protected Processor createCamelProcessor(Endpoint camelEndpoint) {
        Processor processor = null;
        try {
            processor = camelEndpoint.createProducer();
        }
        catch (Exception e) {
            throw new FailedToCreateProducerException(camelEndpoint, e);
        }
        return processor;
    }

    /**
     * Should we expose the Camel JBI onto the NMR.
     * <p/>
     * We may wish to add some policy stuff etc.
     *
     * @param endpoint the camel endpoint
     * @return true if the endpoint should be exposed in the NMR
     */
    public boolean isEndpointExposedOnNmr(Endpoint endpoint) {
        // TODO we should only expose consuming endpoints
        return !(endpoint instanceof JbiEndpoint);
    }
}
