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
package org.apache.camel.component.aws2.ddbstream;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("aws2-ddbstream")
public class Ddb2StreamComponent extends HealthCheckComponent {
    @Metadata
    private Ddb2StreamConfiguration configuration = new Ddb2StreamConfiguration();

    public Ddb2StreamComponent() {
        this(null);
    }

    public Ddb2StreamComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Table name must be specified.");
        }
        Ddb2StreamConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new Ddb2StreamConfiguration();
        configuration.setTableName(remaining);
        Ddb2StreamEndpoint endpoint = new Ddb2StreamEndpoint(uri, configuration, this);
        setProperties(endpoint, parameters);
        if (Boolean.FALSE.equals(configuration.isUseDefaultCredentialsProvider())
                && Boolean.FALSE.equals(configuration.isUseProfileCredentialsProvider())
                && configuration.getAmazonDynamoDbStreamsClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, amazonDDBStreamsClient or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public Ddb2StreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(Ddb2StreamConfiguration configuration) {
        this.configuration = configuration;
    }
}
