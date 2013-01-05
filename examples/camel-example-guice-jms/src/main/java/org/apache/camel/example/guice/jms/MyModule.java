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
package org.apache.camel.example.guice.jms;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Named;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.guice.CamelModuleWithMatchingRoutes;
import org.apache.camel.guice.jndi.JndiBind;

/**
 * Configures the CamelContext, RouteBuilder, Component and Endpoint instances using
 * Guice
 *
 */
public class MyModule extends CamelModuleWithMatchingRoutes {

    @Override
    protected void configure() {
        super.configure();

        // let's add in any RouteBuilder instances we want to use
        bind(MyRouteBuilder.class);
        bind(Printer.class);
    }

    /**
     * Let's configure the JMS component, parameterizing some properties from the
     * jndi.properties file
     */
    @Provides
    @JndiBind("jms")
    JmsComponent jms(@Named("activemq.brokerURL") String brokerUrl) {
        return JmsComponent.jmsComponent(new ActiveMQConnectionFactory(brokerUrl));
    }
    
    @Provides
    @JndiBind("myBean") 
    SomeBean someBean(Injector injector) {
        return injector.getInstance(SomeBean.class); 
    }
}
