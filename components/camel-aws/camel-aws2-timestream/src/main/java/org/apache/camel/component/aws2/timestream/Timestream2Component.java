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
package org.apache.camel.component.aws2.timestream;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.aws2.timestream.query.Timestream2QueryEndpoint;
import org.apache.camel.component.aws2.timestream.write.Timestream2WriteEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component(value = "aws2-timestream")
public class Timestream2Component extends HealthCheckComponent {

    @Metadata
    private Timestream2Configuration configuration = new Timestream2Configuration();

    public Timestream2Component() {
        this(null);
    }

    public Timestream2Component(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Timestream2Configuration configuration
                = this.configuration != null ? this.configuration.copy() : new Timestream2Configuration();

        if (remaining.trim().length() != 0) {
            String[] uriPath = remaining.split(":");
            Timestream2ClientType timestream2ClientType = Timestream2ClientType.valueOf(uriPath[0]);
            if (Timestream2ClientType.write.equals(timestream2ClientType)) {
                Timestream2WriteEndpoint timestream2WriteEndpoint = new Timestream2WriteEndpoint(uri, this, configuration);
                setProperties(timestream2WriteEndpoint, parameters);
                if (Boolean.FALSE.equals(configuration.isUseDefaultCredentialsProvider())
                        && Boolean.FALSE.equals(configuration.isUseProfileCredentialsProvider())
                        && configuration.getAwsTimestreamWriteClient() == null
                        && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
                    throw new IllegalArgumentException(
                            "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, Amazon Timestream Write client or accessKey and secretKey must be specified");
                }
                return timestream2WriteEndpoint;
            } else if (Timestream2ClientType.query.equals(timestream2ClientType)) {
                Timestream2QueryEndpoint timestream2QueryEndpoint = new Timestream2QueryEndpoint(uri, this, configuration);
                setProperties(timestream2QueryEndpoint, parameters);
                if (Boolean.FALSE.equals(configuration.isUseDefaultCredentialsProvider())
                        && Boolean.FALSE.equals(configuration.isUseProfileCredentialsProvider())
                        && configuration.getAwsTimestreamQueryClient() == null
                        && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
                    throw new IllegalArgumentException(
                            "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, Amazon Timestream Query client or accessKey and secretKey must be specified");
                }
                return timestream2QueryEndpoint;
            } else {
                throw new IllegalArgumentException("Invalid Endpoint Type. It should be either write or query");
            }
        } else {
            throw new IllegalArgumentException("Type of Endpoint is missing from uri, it should be either write or query");
        }

    }

    public Timestream2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(Timestream2Configuration configuration) {
        this.configuration = configuration;
    }

}
