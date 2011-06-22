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

package org.apache.camel.component.cxf;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import javax.xml.ws.handler.Handler;

import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxfBlueprintEndpoint extends CxfEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(CxfBlueprintEndpoint.class);

    protected Map<String, Object> properties;

    private List<Handler> handlers;
    private List<String> schemaLocations;

    private BlueprintContainer blueprintContainer;
    private BundleContext bundleContext;
    private BlueprintCamelContext blueprintCamelContext;

    public CxfBlueprintEndpoint(String address) {
        super(address);

    }

    public List<Handler> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    public void destroy() {
        // Clean up the BusFactory's defaultBus
        // This method is not called magically, blueprint
        // needs you to set the destroy-method.
        BusFactory.setDefaultBus(null);
        BusFactory.setThreadDefaultBus(null);
    }

    // Package private methods
    // -------------------------------------------------------------------------

    /**
     * Create a CXF client object
     */
    Client createClient() throws Exception {

        // get service class
        if (getDataFormat().equals(DataFormat.POJO)) {
            ObjectHelper.notEmpty(getServiceClass(), CxfConstants.SERVICE_CLASS);
        }

        if (getWsdlURL() == null && getServiceClass() == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            setServiceClass(org.apache.camel.component.cxf.DefaultSEI.class.getName());
            setDefaultOperationNamespace(CxfConstants.DISPATCH_NAMESPACE);
            setDefaultOperationName(CxfConstants.DISPATCH_DEFAULT_OPERATION_NAMESPACE);
            if (getDataFormat().equals(DataFormat.PAYLOAD)) {
                setSkipPayloadMessagePartCheck(true);
            }
        }

        Class<?> cls = null;
        if (getServiceClass() != null) {
            //Fool CXF classes to load their settings and bindings from the CXF bundle
            cls = bundleContext.getBundle().loadClass(getServiceClass());
            // create client factory bean
            ClientProxyFactoryBean factoryBean = createClientFactoryBean(cls);
            // setup client factory bean
            setupClientFactoryBean(factoryBean, cls);
            return ((ClientProxy) Proxy.getInvocationHandler(factoryBean.create())).getClient();
        } else {
            checkName(getPortName(), "endpoint/port name");
            checkName(getServiceName(), "service name");

            ClientFactoryBean factoryBean = createClientFactoryBean();
            // setup client factory bean
            setupClientFactoryBean(factoryBean);
            return factoryBean.create();
        }
    }

    protected void checkName(Object value, String name) {
        if (ObjectHelper.isEmpty(value)) {
            LOG.warn("The " + name + " of " + this.getEndpointUri() + " is empty, cxf will try to load the first one in wsdl for you.");
        }
    }

    /**
     * Create a CXF server factory bean
     */
    ServerFactoryBean createServerFactoryBean() throws Exception {

        Class<?> cls = null;
        if (getDataFormat() == DataFormat.POJO || getServiceClass() != null) {
            // get service class
            ObjectHelper.notEmpty(getServiceClass(), CxfConstants.SERVICE_CLASS);
            cls = bundleContext.getBundle().loadClass(getServiceClass());
        }

        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;

        if (cls == null) {
            checkName(getPortName(), " endpoint/port name");
            checkName(getServiceName(), " service name");
            answer = new ServerFactoryBean(new WSDLServiceFactoryBean());
        } else if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            answer = new JaxWsServerFactoryBean();
        } else {
            answer = new ServerFactoryBean();
        }
        // setup server factory bean
        setupServerFactoryBean(answer, cls);

        return answer;
    }

    public void setSchemaLocations(List<String> schemaLocations) {
        this.schemaLocations = schemaLocations;
    }

    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public BlueprintContainer getBlueprintContainer() {
        return blueprintContainer;
    }

    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public BlueprintCamelContext getBlueprintCamelContext() {
        return blueprintCamelContext;
    }

    public void setBlueprintCamelContext(BlueprintCamelContext blueprintCamelContext) {
        this.blueprintCamelContext = blueprintCamelContext;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public CxfBlueprintEndpoint getBean() {
        return this;
    }
}
