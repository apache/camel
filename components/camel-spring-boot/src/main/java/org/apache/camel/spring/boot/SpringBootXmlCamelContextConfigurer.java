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
package org.apache.camel.spring.boot;

import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.spi.XmlCamelContextConfigurer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Used to merge Camel Spring Boot configuration with {@link org.apache.camel.CamelContext} that
 * has been created from XML files. This allows to configure your Camel applications with Spring Boot
 * configuration for both Java and XML Camel routes in similar way.
 */
public class SpringBootXmlCamelContextConfigurer implements XmlCamelContextConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootXmlCamelContextConfigurer.class);

    @Override
    public void configure(ApplicationContext applicationContext, SpringCamelContext camelContext) {
        CamelConfigurationProperties config = applicationContext.getBean(CamelConfigurationProperties.class);
        if (config != null) {
            try {
                LOG.debug("Merging XML based CamelContext with Spring Boot configuration properties");
                CamelAutoConfiguration.doConfigureCamelContext(applicationContext, camelContext, config);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }
}
