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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

public class CamelNamespaceHandler extends org.apache.camel.spring.handler.CamelNamespaceHandler {

    public void init() {
        super.init();
        registerParser("camelContext", new CamelContextBeanDefinitionParser(CamelContextFactoryBean.class));
    }

    protected JAXBContext createJaxbContext() throws JAXBException {
        StringBuilder packages = new StringBuilder();
        for (Class cl : getJaxbPackages()) {
            if (packages.length() > 0) {
                packages.append(":");
            }
            packages.append(cl.getName().substring(0, cl.getName().lastIndexOf('.')));
        }
        return JAXBContext.newInstance(packages.toString(), getClass().getClassLoader());
    }

    protected Set<Class> getJaxbPackages() {
        Set<Class> classes = new HashSet<Class>();
        classes.add(org.apache.camel.osgi.CamelContextFactoryBean.class);
        classes.add(org.apache.camel.spring.CamelContextFactoryBean.class);
        classes.add(org.apache.camel.model.RouteType.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);
        classes.add(org.apache.camel.model.dataformat.DataFormatType.class);
        classes.add(org.apache.camel.model.language.ExpressionType.class);
        classes.add(org.apache.camel.model.loadbalancer.LoadBalancerType.class);
        return classes;
    }


}
