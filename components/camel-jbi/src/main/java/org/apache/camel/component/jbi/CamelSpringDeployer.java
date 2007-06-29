/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jbi;

import org.apache.camel.Endpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.servicemix.common.xbean.AbstractXBeanDeployer;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.xbean.kernel.Kernel;
import org.apache.xbean.server.spring.loader.PureSpringLoader;
import org.apache.xbean.server.spring.loader.SpringLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import javax.jbi.management.DeploymentException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A deployer of the spring XML file
 *
 * @version $Revision: 1.1 $
 */
public class CamelSpringDeployer extends AbstractXBeanDeployer {
    private final CamelJbiComponent component;
    private PureSpringLoader springLoader = new PureSpringLoader() {
        @Override
        protected AbstractXmlApplicationContext createXmlApplicationContext(String configLocation) {
            return new FileSystemXmlApplicationContext(new String[]{configLocation}, false, createParentApplicationContext());
        }
    };
    private List<CamelJbiEndpoint> activatedEndpoints = new ArrayList<CamelJbiEndpoint>();

    public CamelSpringDeployer(CamelJbiComponent component) {
        super(component);
        this.component = component;
    }

    protected String getXBeanFile() {
        return "camel-context";
    }

    /* (non-Javadoc)
    * @see org.apache.servicemix.common.Deployer#deploy(java.lang.String, java.lang.String)
    */
    @Override
    public ServiceUnit deploy(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException {
        // lets register the deployer so that any endpoints activated are added to this SU 
        component.deployer = this;
        ServiceUnit serviceUnit = super.deploy(serviceUnitName, serviceUnitRootPath);
        return serviceUnit;
    }

    public void addService(CamelJbiEndpoint endpoint) {
        activatedEndpoints.add(endpoint);
    }

    protected List getServices(Kernel kernel) {
        try {
            List<CamelJbiEndpoint> services = new ArrayList<CamelJbiEndpoint>(activatedEndpoints);
            activatedEndpoints.clear();
                  
            ApplicationContext applicationContext = springLoader.getApplicationContext();
            SpringCamelContext camelContext = SpringCamelContext.springCamelContext(applicationContext);

            // now lets iterate through all the endpoints
            Collection<Endpoint> endpoints = camelContext.getSingletonEndpoints();
            for (Endpoint endpoint : endpoints) {
                if (component.isEndpointExposedOnNmr(endpoint)) {
                    services.add(component.createJbiEndpointFromCamel(endpoint));
                }
            }
            return services;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SpringLoader createSpringLoader() {
        return springLoader;
    }

    /**
     * Returns the parent application context which can be used to auto-wire any JBI based components
     * using the jbi prefix
     */
    protected ApplicationContext createParentApplicationContext() {
        GenericApplicationContext answer = new GenericApplicationContext();
        answer.getBeanFactory().registerSingleton("jbi", component);
        answer.start();
        answer.refresh();
        return answer;
    }


}