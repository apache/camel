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
import org.apache.camel.EndpointResolver;
import org.apache.servicemix.common.DefaultComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Deploys the camel endpoints within JBI
 *
 * @version $Revision: 426415 $
 */
public class CamelJbiComponent extends DefaultComponent implements Component<JbiExchange>, EndpointResolver {
    private JbiBinding binding;
    private CamelContext context;

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

    // Resolve Camel Endpoints
    //-------------------------------------------------------------------------
    public Component resolveComponent(CamelContext context, String uri) throws Exception {
        return null;
    }

    public Endpoint resolveEndpoint(CamelContext context, String uri) throws Exception {
        if (uri.startsWith("jbi:")) {
            uri = uri.substring("jbi:".length());
            JbiEndpoint camelEndpoint = new JbiEndpoint(uri, context, getComponentContext(), getBinding());

            // lets expose this endpoint now in JBI
            // TODO there could already be a component registered in JBI for this??
            CamelJbiEndpoint jbiEndpoint = new CamelJbiEndpoint(camelEndpoint, getBinding());
            addEndpoint(jbiEndpoint);
            return camelEndpoint;
        }
        return null;
    }

    public void setContext(CamelContext context) {
        this.context = context;
    }
}
