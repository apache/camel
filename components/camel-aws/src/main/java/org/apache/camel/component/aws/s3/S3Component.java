/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

public class S3Component extends DefaultComponent {
    
    public S3Component() {
        this(null);
    }

    public S3Component(CamelContext context) {
        super(context);
        
        registerExtension(new S3ComponentVerifierExtension());
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        S3Configuration configuration = new S3Configuration();
        setProperties(configuration, parameters);

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }
        if (remaining.startsWith("arn:")) {
            remaining = remaining.substring(remaining.lastIndexOf(":") + 1, remaining.length());
        }
        configuration.setBucketName(remaining);

        if (configuration.getAmazonS3Client() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonS3Client or accessKey and secretKey must be specified");
        }

        S3Endpoint endpoint = new S3Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
