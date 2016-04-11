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
package org.apache.camel.component.ejb;

import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.camel.Endpoint;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.Registry;

/**
 * EJB component to invoke EJBs like the {@link org.apache.camel.component.bean.BeanComponent}.
 *
 * @version 
 */
public class EjbComponent extends BeanComponent {

    private Context context;
    private Properties properties;

    public EjbComponent() {
        super(EjbEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EjbEndpoint answer = new EjbEndpoint(uri, this);
        answer.setBeanName(remaining);

        // plugin registry to lookup in jndi for the EJBs
        Registry registry = new JndiRegistry(getContext());
        // and register the bean as a holder on the endpoint
        BeanHolder holder = new EjbRegistryBean(registry, getCamelContext(), answer.getBeanName());
        answer.setBeanHolder(holder);

        return answer;
    }

    public Context getContext() throws NamingException {
        return context;
    }

    /**
     * The Context to use for looking up the EJBs
     */
    public void setContext(Context context) {
        this.context = context;
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * Properties for creating javax.naming.Context if a context has not been configured.
     */
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (context == null && properties != null) {
            context = new InitialContext(getProperties());
        }
    }
}
