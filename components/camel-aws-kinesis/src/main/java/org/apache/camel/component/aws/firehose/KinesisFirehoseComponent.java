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
package org.apache.camel.component.aws.firehose;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehose;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-kinesis-firehose")
public class KinesisFirehoseComponent extends DefaultComponent {

    @Metadata
    private KinesisFirehoseConfiguration configuration = new KinesisFirehoseConfiguration();

    public KinesisFirehoseComponent() {
        this(null);
    }

    public KinesisFirehoseComponent(CamelContext context) {
        super(context);

        registerExtension(new KinesisFirehoseComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        KinesisFirehoseConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new KinesisFirehoseConfiguration();
        configuration.setStreamName(remaining);
        KinesisFirehoseEndpoint endpoint = new KinesisFirehoseEndpoint(uri, configuration, this);
        setProperties(endpoint, parameters);
        if (endpoint.getConfiguration().isAutoDiscoverClient()) {
            checkAndSetRegistryClient(configuration);
        }
        if (configuration.getAmazonKinesisFirehoseClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonKinesisFirehoseClient or accessKey and secretKey must be specified");
        }
        return endpoint;
    }

    public KinesisFirehoseConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(KinesisFirehoseConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(KinesisFirehoseConfiguration configuration) {
        Set<AmazonKinesisFirehose> clients = getCamelContext().getRegistry().findByType(AmazonKinesisFirehose.class);
        if (clients.size() == 1) {
            configuration.setAmazonKinesisFirehoseClient(clients.stream().findFirst().get());
        }
    }
}
