/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.boot;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.main.BaseRoutesCollector;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

/**
 * Spring Boot {@link org.apache.camel.main.RoutesCollector}.
 */
public class SpringBootRoutesCollector extends BaseRoutesCollector {

    private final ApplicationContext applicationContext;

    public SpringBootRoutesCollector(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<RoutesDefinition> collectXmlRoutesFromDirectory(CamelContext camelContext, String directory) throws Exception {
        List<RoutesDefinition> answer = new ArrayList<>();

        String[] parts = directory.split(",");
        for (String part : parts) {
            log.info("Loading additional Camel XML routes from: {}", part);
            try {
                Resource[] xmlRoutes = applicationContext.getResources(part);
                for (Resource xmlRoute : xmlRoutes) {
                    log.debug("Found XML route: {}", xmlRoute);
                    RoutesDefinition routes = ModelHelper.loadRoutesDefinition(camelContext, xmlRoute.getInputStream());
                    answer.add(routes);
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML routes found in {}. Skipping XML routes detection.", part);
            }
        }

        return answer;
    }

    @Override
    public List<RestsDefinition> collectXmlRestsFromDirectory(CamelContext camelContext, String directory) throws Exception {
        List<RestsDefinition> answer = new ArrayList<>();

        String[] parts = directory.split(",");
        for (String part : parts) {
            log.info("Loading additional Camel XML rests from: {}", part);
            try {
                final Resource[] xmlRests = applicationContext.getResources(part);
                for (final Resource xmlRest : xmlRests) {
                    RestsDefinition rests = ModelHelper.loadRestsDefinition(camelContext, xmlRest.getInputStream());
                    answer.add(rests);
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML rests found in {}. Skipping XML rests detection.", part);
            }
        }

        return answer;
    }

}
