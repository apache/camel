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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("aws2-s3")
public class AWS2S3Component extends HealthCheckComponent {
    @Metadata
    private AWS2S3Configuration configuration = new AWS2S3Configuration();

    public AWS2S3Component() {
        this(null);
    }

    public AWS2S3Component(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }
        if (remaining.startsWith("arn:")) {
            remaining = remaining.substring(remaining.lastIndexOf(':') + 1, remaining.length());
        }
        final AWS2S3Configuration configuration
                = this.configuration != null ? this.configuration.copy() : new AWS2S3Configuration();
        configuration.setBucketName(remaining);
        AWS2S3Endpoint endpoint = new AWS2S3Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (Boolean.FALSE.equals(configuration.isUseDefaultCredentialsProvider())
                && Boolean.FALSE.equals(configuration.isUseProfileCredentialsProvider())
                && configuration.getAmazonS3Client() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, AmazonS3Client or accessKey and secretKey must be specified");
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
}
