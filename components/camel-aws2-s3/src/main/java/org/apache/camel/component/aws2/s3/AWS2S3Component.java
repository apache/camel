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
package org.apache.camel.component.aws2.s3;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

@Component("aws2-s3")
public class AWS2S3Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Component.class);

    @Metadata
    private AWS2S3Configuration configuration = new AWS2S3Configuration();

    public AWS2S3Component() {
        this(null);
    }

    public AWS2S3Component(CamelContext context) {
        super(context);

        registerExtension(new AWS2S3ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }
        if (remaining.startsWith("arn:")) {
            remaining = remaining.substring(remaining.lastIndexOf(":") + 1, remaining.length());
        }
        final AWS2S3Configuration configuration = this.configuration != null ? this.configuration.copy() : new AWS2S3Configuration();
        configuration.setBucketName(remaining);
        AWS2S3Endpoint endpoint = new AWS2S3Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration, endpoint);
        if (!configuration.isUseIAMCredentials() && configuration.getAmazonS3Client() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("useIAMCredentials is set to false, AmazonS3Client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public AWS2S3Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(AWS2S3Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(AWS2S3Configuration configuration, AWS2S3Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAmazonS3Client())) {
            LOG.debug("Looking for an S3Client instance in the registry");
            Set<S3Client> clients = getCamelContext().getRegistry().findByType(S3Client.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one S3Client instance in the registry");
                configuration.setAmazonS3Client(clients.stream().findFirst().get());
            } else {
                LOG.debug("No S3Client instance in the registry");
            }
        } else {
            LOG.debug("S3Client instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
