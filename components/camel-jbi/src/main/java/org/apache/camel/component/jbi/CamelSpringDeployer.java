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

import org.apache.servicemix.common.xbean.AbstractXBeanDeployer;
import org.apache.xbean.kernel.Kernel;
import org.apache.xbean.server.spring.loader.PureSpringLoader;
import org.apache.xbean.server.spring.loader.SpringLoader;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.Endpoint;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A deployer of the spring XML file
 *
 * @version $Revision: 1.1 $
 */
public class CamelSpringDeployer extends AbstractXBeanDeployer {
    private PureSpringLoader springLoader = new PureSpringLoader();
    private final CamelJbiComponent component;

    public CamelSpringDeployer(CamelJbiComponent component) {
        super(component);
        this.component = component;
    }

    protected String getXBeanFile() {
        return "camel-context";
    }

    protected List getServices(Kernel kernel) {
        try {
            List services = new ArrayList();

            ApplicationContext applicationContext = springLoader.getApplicationContext();
            SpringCamelContext camelContext = SpringCamelContext.springCamelContext(applicationContext);

            // now lets iterate through all the endpoints
            Collection<Endpoint> endpoints = camelContext.getSingletonEndpoints();
            for (Endpoint endpoint : endpoints) {
                services.add(component.createJbiEndpointFromCamel(endpoint));
            }
            return services;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SpringLoader createSpringLoader() {
        return springLoader;
    }
}