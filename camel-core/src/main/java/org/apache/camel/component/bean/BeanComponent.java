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
package org.apache.camel.component.bean;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An alternative to the <a href="http://activemq.apache.org/pojo.html">POJO Component</a>
 * which implements the <a href="http://activemq.apache.org/bean.html">Bean Component</a>
 * which will look up the URI in the Spring ApplicationContext and use that to handle message dispatching.
 *
 * @version $Revision: 1.1 $
 */
public class BeanComponent extends DefaultComponent {
    private static final Log LOG = LogFactory.getLog(BeanComponent.class);
    private ParameterMappingStrategy parameterMappingStrategy;

    public BeanComponent() {
    }

    /**
     * A helper method to create a new endpoint from a bean with a generated URI
     */
    public ProcessorEndpoint createEndpoint(Object bean) {
        String uri = "bean:generated:" + bean;
        return createEndpoint(bean, uri);
    }

    /**
     * A helper method to create a new endpoint from a bean with a given URI
     */
    public ProcessorEndpoint createEndpoint(Object bean, String uri) {
        BeanProcessor processor = new BeanProcessor(bean, getCamelContext(), getParameterMappingStrategy());
        return createEndpoint(uri, processor);
    }

    public ParameterMappingStrategy getParameterMappingStrategy() {
        if (parameterMappingStrategy == null) {
            parameterMappingStrategy = createParameterMappingStrategy();
        }
        return parameterMappingStrategy;
    }

    public void setParameterMappingStrategy(ParameterMappingStrategy parameterMappingStrategy) {
        this.parameterMappingStrategy = parameterMappingStrategy;
    }

    // Implementation methods
    //-----------------------------------------------------------------------

    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        Object bean = getBean(remaining);
        BeanProcessor processor = new BeanProcessor(bean, getCamelContext(), getParameterMappingStrategy());
        setProperties(processor, parameters);
        return createEndpoint(uri, processor);
    }

    public Object getBean(String remaining) throws NoBeanAvailableException {
        Registry registry = getCamelContext().getRegistry();
        Object bean = registry.lookup(remaining);
        if (bean == null) {
            throw new NoBeanAvailableException(remaining);
        }
        return bean;
    }

    protected ProcessorEndpoint createEndpoint(String uri, BeanProcessor processor) {
        ProcessorEndpoint answer = new ProcessorEndpoint(uri, this, processor);
        answer.setExchangePattern(ExchangePattern.InOut);
        return answer;
    }
               
    protected ParameterMappingStrategy createParameterMappingStrategy() {
        return BeanProcessor.createParameterMappingStrategy(getCamelContext());
    }
}
