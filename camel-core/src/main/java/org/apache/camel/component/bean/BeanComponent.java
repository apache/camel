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
package org.apache.camel.component.bean;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;

/**
 * An alternative to the <a href="http://activemq.apache.org/pojo.html">POJO Component</a>
 * which implements the <a href="http://activemq.apache.org/bean.html">Bean Component</a>
 * which will look up the URI in the Spring ApplicationContext and use that to handle message dispatching.
 *
 * @version $Revision: 1.1 $
 */
public class BeanComponent extends DefaultComponent {
    private static final Log log = LogFactory.getLog(BeanComponent.class);
    private MethodInvocationStrategy invocationStrategy;

    public BeanComponent() {
    }

    public MethodInvocationStrategy getInvocationStrategy() {
        if (invocationStrategy == null) {
            invocationStrategy = createInvocationStrategy();
        }
        return invocationStrategy;
    }

    public void setInvocationStrategy(MethodInvocationStrategy invocationStrategy) {
        this.invocationStrategy = invocationStrategy;
    }

    // Implementation methods
    //-----------------------------------------------------------------------

    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        Object bean = getBean(remaining);
        BeanProcessor processor = new BeanProcessor(bean, getInvocationStrategy());
        IntrospectionSupport.setProperties(processor, parameters);
        return new ProcessorEndpoint(uri, this, processor);
    }

    public Object getBean(String remaining) throws NoBeanAvailableException {
        Registry registry = getCamelContext().getRegistry();
        Object bean = registry.lookup(remaining);
        if (bean == null) {
            throw new NoBeanAvailableException(remaining);
        }
        return bean;
    }

    protected MethodInvocationStrategy createInvocationStrategy() {
        CamelContext context = getCamelContext();
        // TODO add this to the context?
        return new DefaultMethodInvocationStrategy();
    }
}
