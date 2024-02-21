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
package org.apache.camel.component.jooq;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("jooq")
public class JooqComponent extends HealthCheckComponent {

    @Metadata(description = "Component configuration (database connection, database entity type, etc.)")
    private JooqConfiguration configuration;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        JooqConfiguration conf = configuration != null ? configuration.copy() : new JooqConfiguration();

        JooqEndpoint endpoint = new JooqEndpoint(uri, this, conf);
        setProperties(endpoint, parameters);
        initConfiguration(getCamelContext(), conf, remaining);
        return endpoint;
    }

    public JooqConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JooqConfiguration jooqConfiguration) {
        this.configuration = jooqConfiguration;
    }

    private static void initConfiguration(CamelContext camelContext, JooqConfiguration configuration, String remaining) {
        if (remaining == null) {
            return;
        }

        String[] parts = remaining.split("/");
        if (parts.length == 0 || parts.length > 2) {
            throw new IllegalArgumentException("Unexpected URI format. Expected ... , found '" + remaining + "'");
        }

        String className = parts[0];
        Class<?> type = camelContext.getClassResolver().resolveClass(className);
        if (type != null) {
            configuration.setEntityType(type);
        }

        if (parts.length > 1) {
            String op = parts[1];
            JooqOperation operation = camelContext.getTypeConverter().convertTo(JooqOperation.class, op);
            if (operation != null) {
                configuration.setOperation(operation);
            } else {
                throw new IllegalArgumentException("Wrong operation: " + op);
            }
        }
    }

}
