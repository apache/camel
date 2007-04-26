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
package org.apache.camel.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.context.ApplicationContext;

/**
 * An extension of the {@link RouteBuilder} to provide some additional helper methods
 *
 * @version $Revision: 1.1 $
 */
public abstract class SpringRouteBuilder extends RouteBuilder {
    private ApplicationContext applicationContext;

    /**
     * Looks up the bean with the given name in the application context and returns it, or throws an exception if the
     * bean is not present or is not of the given type
     *
     * @param type     the type of the bean
     * @param beanName the name of the bean in the application context
     * @return the bean
     */
    public <T> T bean(Class<T> type, String beanName) {
        ApplicationContext context = getApplicationContext();
        return (T) context.getBean(beanName, type);
    }

    /**
     * Looks up the bean with the given type in the application context and returns it, or throws an exception if the
     * bean is not present or there are multiple possible beans to choose from for the given type
     *
     * @param type the type of the bean
     * @return the bean
     */
    public <T> T bean(Class<T> type) {
        ApplicationContext context = getApplicationContext();
        String[] names = context.getBeanNamesForType(type, true, true);
        if (names != null) {
            int count = names.length;
            if (count == 1) {
                // lets instantiate the single bean
                return (T) context.getBean(names[0]);
            }
            else if (count > 1) {
                throw new IllegalArgumentException("Too many beans in the application context of type: " + type + ". Found: " + count);
            }
        }
        throw new IllegalArgumentException("No bean available in the application context of type: " + type);
    }

    /**
     * Returns the application context which has been configured via the {@link #setApplicationContext(ApplicationContext)}
     * method  or from the underlying {@link SpringCamelContext}
     * 
     * @return
     */
    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            CamelContext camelContext = getContext();
            if (camelContext instanceof SpringCamelContext) {
                SpringCamelContext springCamelContext = (SpringCamelContext) camelContext;
                return springCamelContext.getApplicationContext();
            }
            else {
                throw new IllegalArgumentException("This SpringBuilder is not being used with a SpringCamelContext and there is no applicationContext property configured");
            }
        }
        return applicationContext;
    }

    /**
     * Sets the application context to use to lookup beans
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
