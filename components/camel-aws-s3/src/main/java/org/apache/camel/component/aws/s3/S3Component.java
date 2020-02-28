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
package org.apache.camel.component.aws.s3;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.s3.AmazonS3;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-s3")
public class S3Component extends DefaultComponent {

    @Metadata
    private S3Configuration configuration = new S3Configuration();

    public S3Component() {
        this(null);
    }

    public S3Component(CamelContext context) {
        super(context);

        registerExtension(new S3ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }
        if (remaining.startsWith("arn:")) {
            remaining = remaining.substring(remaining.lastIndexOf(":") + 1, remaining.length());
        }
        final S3Configuration configuration = this.configuration != null ? this.configuration.copy() : new S3Configuration();
        configuration.setBucketName(remaining);
        S3Endpoint endpoint = new S3Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (!configuration.isUseIAMCredentials() && configuration.getAmazonS3Client() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("useIAMCredentials is set to false, AmazonS3Client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public S3Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(S3Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(S3Configuration configuration) {
        Set<AmazonS3> clients = getCamelContext().getRegistry().findByType(AmazonS3.class);
        if (clients.size() == 1) {
            configuration.setAmazonS3Client(clients.stream().findFirst().get());
        }
    }
}
