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

import java.util.Collections;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainSupport;
import org.springframework.context.ApplicationContext;

public class CamelSpringBootApplicationController {

    private final MainSupport mainSupport;

    public CamelSpringBootApplicationController(final ApplicationContext applicationContext, final CamelContext camelContext) {
        this.mainSupport = new MainSupport() {
            @Override
            protected ProducerTemplate findOrCreateCamelTemplate() {
                return applicationContext.getBean(ProducerTemplate.class);
            }

            @Override
            protected Map<String, CamelContext> getCamelContextMap() {
                return Collections.singletonMap("camelContext", camelContext);
            }
        };
    }

    public void blockMainThread() {
        try {
            mainSupport.enableHangupSupport();
            mainSupport.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    private void destroy() {
        mainSupport.completed();
    }

}