/*
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
package org.apache.camel.component.cron;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cron.api.CamelCronConfiguration;
import org.apache.camel.component.cron.api.CamelCronService;

/**
 * Allows camel-spring to be used as implementation for camel-cron endpoints.
 */
public class CamelSpringCronService implements CamelCronService, CamelContextAware {

    private CamelContext context;

    @Override
    public Endpoint createEndpoint(CamelCronConfiguration configuration) throws Exception {
        CronComponent cronComponent = context.getComponent("cron", CronComponent.class);
        String uri = "cron:" + configuration.getName();
        SpringCronEndpoint cronEndpoint = new SpringCronEndpoint(uri, cronComponent);
        Map<String, Object> options = new HashMap<>();
        options.put("scheduler", "spring");
        options.put("scheduler.cron", configuration.getSchedule());
        cronEndpoint.configureProperties(options);
        return cronEndpoint;
    }

    @Override
    public String getId() {
        return "spring";
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.context = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.context;
    }

}
