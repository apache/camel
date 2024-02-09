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
package org.apache.camel.component.aws2.kinesis;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.IOHelper;

@Component("aws2-kinesis")
public class Kinesis2Component extends HealthCheckComponent {

    @Metadata
    private Kinesis2Configuration configuration = new Kinesis2Configuration();

    private KinesisConnection connection = new KinesisConnection();

    public Kinesis2Component() {
        this(null);
    }

    public Kinesis2Component(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Kinesis2Configuration configuration
                = this.configuration != null ? this.configuration.copy() : new Kinesis2Configuration();
        configuration.setStreamName(remaining);
        Kinesis2Endpoint endpoint = new Kinesis2Endpoint(uri, configuration, this);
        setProperties(endpoint, parameters);
        if (!configuration.isUseDefaultCredentialsProvider() && !configuration.isUseProfileCredentialsProvider()
                && !configuration.isUseSessionCredentials()
                && configuration.getAmazonKinesisClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, useSessionCredentials is set to false, AmazonKinesisClient or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public Kinesis2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(Kinesis2Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        // use pre-configured client (if any)
        connection.setKinesisClient(configuration.getAmazonKinesisClient());
    }

    @Override
    protected void doStop() throws Exception {
        IOHelper.close(connection);
    }

    public KinesisConnection getConnection() {
        return connection;
    }
}
