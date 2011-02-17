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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.cxf.message.Message;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Component</a> 
 * 
 * @version 
 */
public class CxfComponent extends HeaderFilterStrategyComponent {
        
    public CxfComponent() {
    }

    public CxfComponent(CamelContext context) {
        super(context);
    }
    
    /**
     * Create a {@link CxfEndpoint} which, can be a Spring bean endpoint having 
     * URI format cxf:bean:<i>beanId</i> or transport address endpoint having URI format
     * cxf://<i>transportAddress</i>. 
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, 
            Map<String, Object> parameters) throws Exception {
        
        CxfEndpoint result = null;
        
        if (remaining.startsWith(CxfConstants.SPRING_CONTEXT_ENDPOINT)) {
            // Get the bean from the Spring context
            String beanId = remaining.substring(CxfConstants.SPRING_CONTEXT_ENDPOINT.length());
            if (beanId.startsWith("//")) {
                beanId = beanId.substring(2);
            }

            CxfEndpointBean bean = CamelContextHelper.mandatoryLookup(getCamelContext(), beanId, 
                    CxfEndpointBean.class);

            result = new CxfSpringEndpoint(this, bean);
           
            // Apply Spring bean properties (including # notation referenced bean).  Note that the
            // Spring bean properties values can be overridden by property defined in URI query.
            // The super class (DefaultComponent) will invoke "setProperties" after this method 
            // with to apply properties defined by URI query. 
            if (bean.getProperties() != null) {
                Map<String, Object> copy = new HashMap<String, Object>();
                copy.putAll(bean.getProperties());     
                setProperties(result, copy);      
                result.setMtomEnabled(Boolean.valueOf((String)copy.get(Message.MTOM_ENABLED)));
            }
            
        } else {
            // endpoint URI does not specify a bean
            result = new CxfEndpoint(remaining, this);
        }
        
        setEndpointHeaderFilterStrategy(result);
        return result;
    }
    
    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        CxfEndpoint cxfEndpoint = (CxfEndpoint) endpoint;
        cxfEndpoint.updateEndpointUri(uri);
    }
        
}
