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
package org.apache.camel.component.aws.securityhub;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws.securityhub.client.SecurityHubClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;

/**
 * Manage and interact with AWS Security Hub for security findings.
 */
@UriEndpoint(firstVersion = "4.18.0", scheme = "aws-security-hub", title = "AWS Security Hub",
             syntax = "aws-security-hub:label", producerOnly = true, category = {
                     Category.CLOUD,
                     Category.SECURITY },
             headersClass = SecurityHubConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO),
                                  description = "batchImportFindings, the default, imports the findings of the"
                                                + " CamelAwsSecurityHubFindings header, or else of the body: a Collection or an"
                                                + " array of the AwsSecurityFinding of the AWS SDK, or one AwsSecurityFinding."
                                                + " JSON text, a Map, or a Collection or an array of anything else fails. The"
                                                + " other operations take"
                                                + " their input from headers, such as CamelAwsSecurityHubFilters. With"
                                                + " pojoRequest=true the request of the AWS SDK for the operation, such as a"
                                                + " GetFindingsRequest: another body fails"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   description = "The response of the AWS SDK for the operation, such as the"
                                                 + " BatchImportFindingsResponse of batchImportFindings, with the counts in the"
                                                 + " CamelAwsSecurityHubSuccessCount and CamelAwsSecurityHubFailedCount"
                                                 + " headers: a finding that is not imported fails the exchange."
                                                 + " getFindings gives a GetFindingsResponse, with the token of the next page"
                                                 + " in the CamelAwsSecurityHubNextToken header"))
public class SecurityHubEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private SecurityHubClient securityHubClient;

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam
    private SecurityHubConfiguration configuration;

    public SecurityHubEndpoint(String uri, Component component, SecurityHubConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public SecurityHubComponent getComponent() {
        return (SecurityHubComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SecurityHubProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        securityHubClient = configuration.getSecurityHubClient() != null
                ? configuration.getSecurityHubClient()
                : SecurityHubClientFactory.getSecurityHubClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getSecurityHubClient())) {
            if (securityHubClient != null) {
                securityHubClient.close();
            }
        }
        super.doStop();
    }

    public SecurityHubConfiguration getConfiguration() {
        return configuration;
    }

    public SecurityHubClient getSecurityHubClient() {
        return securityHubClient;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Logical name
     */
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "securityhub";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (label != null) {
            return Map.of("label", label);
        }
        return null;
    }
}
