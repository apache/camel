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
package org.apache.camel.component.cxf.jaxrs;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CastUtils;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;

/**
 * Defines the <a href="http://camel.apache.org/cxfrs.html">CXF RS Component</a> 
 */
public class CxfRsComponent extends HeaderFilterStrategyComponent {

    public CxfRsComponent() {
        super();
    }
    
    public CxfRsComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        CxfRsEndpoint answer = null;
        if (remaining.startsWith(CxfConstants.SPRING_CONTEXT_ENDPOINT)) {
            // Get the bean from the Spring context
            String beanId = remaining.substring(CxfConstants.SPRING_CONTEXT_ENDPOINT.length());
            if (beanId.startsWith("//")) {
                beanId = beanId.substring(2);
            }

            AbstractJAXRSFactoryBean bean = CamelContextHelper.mandatoryLookup(getCamelContext(), beanId, 
                AbstractJAXRSFactoryBean.class);

            answer = new CxfRsSpringEndpoint(this.getCamelContext(), bean);
           
            // Apply Spring bean properties (including # notation referenced bean).  Note that the
            // Spring bean properties values can be overridden by property defined in URI query.
            // The super class (DefaultComponent) will invoke "setProperties" after this method 
            // with to apply properties defined by URI query. 
            if (bean.getProperties() != null) {
                Map<String, Object> copy = new HashMap<String, Object>();
                copy.putAll(bean.getProperties());     
                setProperties(answer, copy);      
            }
            
        } else {
            // endpoint URI does not specify a bean
            answer = new CxfRsEndpoint(remaining, this);
        }
        Map<String, String> params = CastUtils.cast(parameters);
        answer.setParameters(params);
        setEndpointHeaderFilterStrategy(answer);
        return answer;
    }
}
