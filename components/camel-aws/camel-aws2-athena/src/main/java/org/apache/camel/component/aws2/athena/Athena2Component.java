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
package org.apache.camel.component.aws2.athena;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

/**
 * For working with Amazon Athena SDK v2.
 */
@Component("aws2-athena")
public class Athena2Component extends HealthCheckComponent {

    @Metadata
    private Athena2Configuration configuration = new Athena2Configuration();

    public Athena2Component() {
        this(null);
    }

    public Athena2Component(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Athena2Configuration configurationClone
                = this.configuration != null ? this.configuration.copy() : new Athena2Configuration();
        Athena2Endpoint endpoint = new Athena2Endpoint(uri, this, configurationClone);
        setProperties(endpoint, parameters);
        if (Boolean.FALSE.equals(configurationClone.isUseDefaultCredentialsProvider())
                && Boolean.FALSE.equals(configurationClone.isUseProfileCredentialsProvider())
                && configurationClone.getAmazonAthenaClient() == null
                && (configurationClone.getAccessKey() == null
                        || configurationClone.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false,accessKey/secretKey or amazonAthenaClient must be specified");
        }
        return endpoint;
    }

    public Athena2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration.
     */
    public void setConfiguration(Athena2Configuration configuration) {
        this.configuration = configuration;
    }
}
