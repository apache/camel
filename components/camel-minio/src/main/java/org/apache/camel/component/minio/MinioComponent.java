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
package org.apache.camel.component.minio;

import java.util.Map;
import java.util.Set;

import io.minio.MinioClient;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

@Component("minio")
public class MinioComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(MinioComponent.class);

    @Metadata
    private MinioConfiguration configuration = new MinioConfiguration();

    public MinioComponent() {
        this(null);
    }

    public MinioComponent(CamelContext context) {
        super(context);
        registerExtension(new MinioComponentVerifierExtension());
    }

    @Override
    protected MinioEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (isEmpty(remaining) || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }

        final MinioConfiguration configuration
                = isNotEmpty(this.configuration) ? this.configuration.copy() : new MinioConfiguration();
        configuration.setBucketName(remaining);
        MinioEndpoint endpoint = new MinioEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);

        return endpoint;
    }

    public MinioConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(MinioConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(MinioConfiguration configuration, MinioEndpoint endpoint) {
        if (isEmpty(endpoint.getConfiguration().getMinioClient())) {
            LOG.debug("Looking for an MinioClient instance in the registry");
            Set<MinioClient> clients = getCamelContext().getRegistry().findByType(MinioClient.class);
            if (clients.size() > 1) {
                LOG.debug("Found more than one MinioClient instance in the registry");
            } else if (clients.size() == 1) {
                LOG.debug("Found exactly one MinioClient instance in the registry");
                configuration.setMinioClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No MinioClient instance in the registry");
            }
        } else {
            LOG.debug("MinioClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
