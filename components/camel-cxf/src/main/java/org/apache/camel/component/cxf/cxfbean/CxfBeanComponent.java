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
package org.apache.camel.component.cxf.cxfbean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.HeaderFilterStrategyComponent;

/**
 * CXF Bean component creates {@link CxfBeanEndpoint} which represents a
 * bean.  <b>Currently, only JAXRS annotated beans are supported.  In the
 * future, JAXwS annotated beans and POJO can be supported</b>.
 * 
 * @deprecated
 */
@Deprecated
public class CxfBeanComponent extends HeaderFilterStrategyComponent {

    private Map<String, CxfBeanEndpoint> endpoints = new HashMap<String, CxfBeanEndpoint>();

    public CxfBeanComponent() {
        super(CxfBeanEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining,
            Map<String, Object> parameters) throws Exception {

        // Extract the comma separated list of providers in advance of the auto-extraction
        // that a DefaultEndpoint will perform (as the default one does not understand lists).
        List<Object> providers = resolveAndRemoveReferenceListParameter(parameters, "providers", Object.class);

        CxfBeanEndpoint answer = new CxfBeanEndpoint(remaining, this);
        if (providers != null) {
            answer.setProviders(providers);
        }
        setEndpointHeaderFilterStrategy(answer);
        setProperties(answer, parameters);

        // add to the endpoints map before calling the endpoint's init() method to 
        // make sure the the CxfBeanDestination activate() method can find the endpoint 
        // from the map.
        endpoints.put(answer.createEndpointUri(), answer);
        answer.init();

        return answer;
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        // we invoke setProperties ourselves so the bus is set for CxfBeanEndpoint.init()
        return false;
    }

    public CxfBeanEndpoint getEndpoint(String endpointUri) {
        return endpoints.get(endpointUri);
    }
   
}
