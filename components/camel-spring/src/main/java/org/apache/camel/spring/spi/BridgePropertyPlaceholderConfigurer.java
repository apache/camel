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
package org.apache.camel.spring.spi;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesResolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

/**
 * A {@link PropertyPlaceholderConfigurer} that bridges Camel's <a href="http://camel.apache.org/using-propertyplaceholder.html">
 * property placeholder</a> with the Spring property placeholder mechanism.
 */
public class BridgePropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer implements PropertiesResolver {

    // NOTE: this class must be in the spi package as if its in the root package, then Spring fails to parse the XML
    // files due some weird spring issue. But that is okay as having this class in the spi package is fine anyway.

    private final Properties properties = new Properties();
    private PropertiesResolver resolver;
    private String id;

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props) throws BeansException {
        // store all the spring properties so we can refer to them later
        properties.putAll(props);
        super.processProperties(beanFactoryToProcess, props);
    }

    @Override
    public void setBeanName(String beanName) {
        this.id = beanName;
        super.setBeanName(beanName);
    }

    @Override
    public Properties resolveProperties(CamelContext context, boolean ignoreMissingLocation, String... uri) throws Exception {
        // return the spring properties, if it
        Properties answer = new Properties();
        for (String u : uri) {
            String ref = "ref:" + id;
            if (ref.equals(u)) {
                answer.putAll(properties);
            } else if (resolver != null) {
                Properties p = resolver.resolveProperties(context, ignoreMissingLocation, u);
                if (p != null) {
                    answer.putAll(p);
                }
            }
        }
        if (answer.isEmpty()) {
            return null;
        } else {
            return answer;
        }
    }

    public void setResolver(PropertiesResolver resolver) {
        this.resolver = resolver;
    }
}
