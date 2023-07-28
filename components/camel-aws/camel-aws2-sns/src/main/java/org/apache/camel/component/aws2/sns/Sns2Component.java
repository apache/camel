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
package org.apache.camel.component.aws2.sns;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

@Component("aws2-sns")
public class Sns2Component extends HealthCheckComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Sns2Component.class);

    @Metadata
    private Sns2Configuration configuration = new Sns2Configuration();

    public Sns2Component() {
        this(null);
    }

    public Sns2Component(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Topic name must be specified.");
        }

        if (containsTransientParameters(parameters)) {
            Map<String, Object> transientParameters = getTransientParameters(parameters);

            setProperties(getCamelContext(), this, transientParameters);
        }

        Map<String, Object> nonTransientParameters = getNonTransientParameters(parameters);

        Sns2Configuration epConfiguration = this.configuration != null ? this.configuration.copy() : new Sns2Configuration();

        if (remaining.startsWith("arn:")) {
            parseRemaining(epConfiguration, remaining);
        } else {
            epConfiguration.setTopicName(remaining);
            LOG.debug("Created the endpoint with topic {}", epConfiguration.getTopicName());
        }

        Sns2Endpoint endpoint = new Sns2Endpoint(uri, this, epConfiguration);
        setProperties(endpoint, nonTransientParameters);

        if (!epConfiguration.isUseDefaultCredentialsProvider() && !epConfiguration.isUseProfileCredentialsProvider()
                && epConfiguration.getAmazonSNSClient() == null
                && (epConfiguration.getAccessKey() == null || epConfiguration.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, AmazonSNSClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    /*
     This method, along with getTransientParameters, getNonTransientParameters and validateParameters handle transient
     parameters. Transient parameters, in this sense, means temporary parameters passed to the URI, that should
     no be directly set on the endpoint because they apply to a different lifecycle in the component/endpoint creation.
     For example, the "configuration" parameter is used to set a different Component/Endpoint configuration class other
     than the one provided by Camel. Because the configuration object is required to configure these objects, it must
     be used earlier in the life cycle ... and not later as part of the transport setup. Therefore, transient.
     */
    private boolean containsTransientParameters(Map<String, Object> parameters) {
        return parameters.containsKey("configuration");
    }

    private Map<String, Object> getNonTransientParameters(Map<String, Object> parameters) {
        return parameters.entrySet().stream().filter(k -> !k.getKey().equals("configuration"))
                .collect(Collectors.toMap(k -> k.getKey(), k -> k.getValue()));
    }

    private Map<String, Object> getTransientParameters(Map<String, Object> parameters) {
        return parameters.entrySet().stream().filter(k -> k.getKey().equals("configuration"))
                .collect(Collectors.toMap(k -> k.getKey(), k -> k.getValue()));
    }

    @Override
    protected void validateParameters(String uri, Map<String, Object> parameters, String optionPrefix) {
        super.validateParameters(uri, getNonTransientParameters(parameters), optionPrefix);
    }

    private void parseRemaining(Sns2Configuration epConfiguration, String remaining) {
        String[] parts = remaining.split(":");
        if (parts.length != 6 || !parts[2].equals("sns")) {
            throw new IllegalArgumentException("Topic arn must be in format arn:aws:sns:region:account:name.");
        }
        epConfiguration.setTopicArn(remaining);
        epConfiguration.setRegion(Region.of(parts[3]).toString());

        LOG.debug("Created the endpoint with topic arn {}", epConfiguration.getTopicArn());
    }

    public Sns2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(Sns2Configuration configuration) {
        this.configuration = configuration;
    }

}
