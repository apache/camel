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
    private String accessKey;
    @Metadata
    private String secretKey;
    @Metadata
    private String region;
    @Metadata(label = "advanced")
    private S3Configuration configuration;

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
        endpoint.getConfiguration().setAccessKey(accessKey);
        endpoint.getConfiguration().setSecretKey(secretKey);
        endpoint.getConfiguration().setRegion(region);
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
     * The AWS S3 default configuration
     */
    public void setConfiguration(S3Configuration configuration) {
        this.configuration = configuration;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region where the bucket is located. This option is used in the
     * `com.amazonaws.services.s3.model.CreateBucketRequest`.
     */
    public void setRegion(String region) {
        this.region = region;
    }

    private void checkAndSetRegistryClient(S3Configuration configuration) {
        Set<AmazonS3> clients = getCamelContext().getRegistry().findByType(AmazonS3.class);
        if (clients.size() == 1) {
            configuration.setAmazonS3Client(clients.stream().findFirst().get());
        }
    }
}
