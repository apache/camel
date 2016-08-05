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
package org.apache.camel.osgi;

import java.util.HashSet;
import java.util.Set;

public class CamelNamespaceHandler extends org.apache.camel.spring.handler.CamelNamespaceHandler {

    public void init() {
        super.init();
        registerParser("camelContext", new CamelContextBeanDefinitionParser(CamelContextFactoryBean.class));
    }

    // It just add the package of the class for initiate the JAXB context
    protected Set<Class<?>> getJaxbPackages() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        classes.add(CamelContextFactoryBean.class);
        classes.add(org.apache.camel.spring.CamelContextFactoryBean.class);
        classes.add(org.apache.camel.ExchangePattern.class);
        classes.add(org.apache.camel.model.RouteDefinition.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);     
        classes.add(org.apache.camel.model.dataformat.DataFormatsDefinition.class);
        classes.add(org.apache.camel.model.language.ExpressionDefinition.class);
        classes.add(org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition.class);        
        classes.add(org.apache.camel.model.rest.RestDefinition.class);
        return classes;
    }


}
