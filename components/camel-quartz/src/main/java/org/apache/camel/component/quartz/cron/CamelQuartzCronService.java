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
package org.apache.camel.component.quartz.cron;

import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cron.api.CamelCronConfiguration;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.util.URISupport;

/**
 * Allows the camel-quartz component to be used as implementation for camel-cron endpoints.
 */
public class CamelQuartzCronService implements CamelCronService, CamelContextAware {

    private CamelContext context;

    @Override
    public Endpoint createEndpoint(CamelCronConfiguration configuration) throws Exception {
        String schedule = convertSchedule(configuration.getSchedule());

        String uriPath = "quartz://" + configuration.getName();
        String query = URISupport.createQueryString(Collections.singletonMap("cron", schedule));
        String uri = uriPath + "?" + query;

        QuartzComponent quartz = context.getComponent("quartz", QuartzComponent.class);
        return quartz.createEndpoint(uri);
    }

    private String convertSchedule(String schedule) {
        String[] parts = schedule.split("\\s");
        if (parts.length == 5) {
            // Seconds are mandatory in Quartz, let's add them back
            return "0 " + schedule;
        }
        return schedule;
    }

    @Override
    public String getId() {
        return "quartz";
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
