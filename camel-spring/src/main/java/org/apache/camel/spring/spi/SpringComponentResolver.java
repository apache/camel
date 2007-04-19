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
package org.apache.camel.spring.spi;

import static org.apache.camel.util.ObjectHelper.notNull;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.ComponentResolver;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * An implementation of {@link ComponentResolver} which tries to find a Camel {@link Component}
 * in the Spring {@link ApplicationContext} first; if its not there it defaults to the auto-discovery mechanism.
 *
 * @version $Revision$
 */
public class SpringComponentResolver implements ComponentResolver {
    private final ApplicationContext applicationContext;
    private final ComponentResolver nextResolver;

    public SpringComponentResolver(ApplicationContext applicationContext, ComponentResolver nextResolver) {
        notNull(applicationContext, "applicationContext");
        this.applicationContext = applicationContext;
        this.nextResolver = nextResolver;
    }

    public Component resolveComponent(String name, CamelContext context) throws Exception {
        Object bean = null;
        try {
            bean = applicationContext.getBean(name);
        }
        catch (NoSuchBeanDefinitionException e) {
            // ignore its not an error
        }
        if (bean != null) {
            if (bean instanceof Component) {
                return (Component) bean;
            }
            else {
                throw new IllegalArgumentException("Bean with name: " + name + " in spring context is not a Component: " + bean);
            }
        }
        if (nextResolver == null) {
            return null;
        }
        return nextResolver.resolveComponent(name, context);
    }
}
