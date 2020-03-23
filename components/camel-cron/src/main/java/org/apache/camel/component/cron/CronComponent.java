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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cron.api.CamelCronConfiguration;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Another Camel cron component.
 */
@Component("cron")
public class CronComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private String cronService;

    private CamelCronService service;

    public CronComponent() {
    }

    @Override
    public Endpoint createEndpoint(String uri, String remaining, Map<String, Object> properties) throws Exception {
        CamelCronConfiguration configuration = new CamelCronConfiguration();
        configuration.setName(remaining);
        setProperties(configuration, properties);
        validate(configuration);

        Endpoint delegate = this.service.createEndpoint(configuration);
        CronEndpoint cronEndpoint = new CronEndpoint(uri, this, delegate, configuration);

        if (properties.size() > 0) {
            // Additional endpoint properties present
            setProperties(cronEndpoint, properties);
        }

        return cronEndpoint;
    }

    @Override
    protected void doInit() throws Exception {
        initCamelCronService();
    }

    /**
     * Lazy creation of the CamelCronService
     */
    public void initCamelCronService() {
        if (this.service == null) {
            this.service = CronHelper.resolveCamelCronService(
                    getCamelContext(),
                    this.cronService
            );

            if (this.service == null) {
                throw new RuntimeCamelException("Cannot find any CamelCronService: please add a valid implementation, such as 'camel-quartz', in order to use the 'camel-cron' component");
            }

            try {
                getCamelContext().addService(this.service, true, false);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    public CamelCronService getService() {
        return service;
    }

    public String getCronService() {
        return cronService;
    }

    /**
     * The id of the CamelCronService to use when multiple implementations
     * are provided
     */
    public void setCronService(String cronService) {
        this.cronService = cronService;
    }

    private void validate(CamelCronConfiguration configuration) {
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(configuration.getName(), "name");
        ObjectHelper.notNull(configuration.getSchedule(), "schedule");

        String[] parts = configuration.getSchedule().split("\\s");
        if (parts.length < 5 || parts.length > 7) {
            throw new IllegalArgumentException("Invalid number of parts in cron expression. Expected 5 to 7, got: " + parts.length);
        }
    }

}
