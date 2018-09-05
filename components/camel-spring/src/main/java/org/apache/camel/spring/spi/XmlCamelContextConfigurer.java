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

import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.ApplicationContext;

/**
 * Allows to do custom configuration when a new XML based {@link org.apache.camel.spring.SpringCamelContext} has
 * been created. For example we use this to enable camel-spring-boot to configure Camel created
 * from XML files with the existing Spring Boot auto configuration.
 */
public interface XmlCamelContextConfigurer {

    /**
     * Configures XML based CamelContext with the given configuration
     *
     * @param applicationContext the Spring context
     * @param camelContext       the XML based CamelContext
     * @throws Exception is thrown if error during configuration
     */
    void configure(ApplicationContext applicationContext, SpringCamelContext camelContext) throws Exception;
}
