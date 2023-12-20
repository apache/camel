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
package org.apache.camel.component.huaweicloud.smn;

import java.util.HashMap;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.smn.v2.SmnClient;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageRequest;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageRequestBody;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.smn.constants.SmnConstants;
import org.apache.camel.component.huaweicloud.smn.constants.SmnOperations;
import org.apache.camel.component.huaweicloud.smn.constants.SmnProperties;
import org.apache.camel.component.huaweicloud.smn.constants.SmnServices;
import org.apache.camel.component.huaweicloud.smn.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleNotificationProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleNotificationProducer.class);
    private SmnClient smnClient;

    public SimpleNotificationProducer(SimpleNotificationEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {

        ClientConfigurations clientConfigurations = new ClientConfigurations();

        if (smnClient == null) {
            validateAndInitializeSmnClient((SimpleNotificationEndpoint) super.getEndpoint(), clientConfigurations);
        }

        String service = ((SimpleNotificationEndpoint) super.getEndpoint()).getSmnService();

        if (!ObjectHelper.isEmpty(service)) {
            switch (service) {
                case SmnServices.PUBLISH_MESSAGE:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using message publishing service");
                    }
                    performPublishMessageServiceOperations((SimpleNotificationEndpoint) super.getEndpoint(), exchange,
                            clientConfigurations);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Completed publishing message");
                    }
                    break;
                default:
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Unsupported service name {}", service);
                    }
                    throw new UnsupportedOperationException(String.format("service %s is not a supported service", service));
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Service name is null/empty");
            }
            throw new IllegalStateException("service name cannot be null/empty");
        }
    }

    /**
     * Publish message service operations
     *
     * @param endpoint
     * @param exchange
     * @param clientConfigurations
     */
    private void performPublishMessageServiceOperations(
            SimpleNotificationEndpoint endpoint,
            Exchange exchange,
            ClientConfigurations clientConfigurations) {
        PublishMessageResponse response;

        PublishMessageRequestBody apiBody;
        validateServiceConfigurations(endpoint, exchange, clientConfigurations);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Checking operation name");
        }
        switch (clientConfigurations.getOperation()) {

            case SmnOperations.PUBLISH_AS_TEXT_MESSAGE:
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Publishing as text message");
                }
                apiBody = new PublishMessageRequestBody()
                        .withMessage(exchange.getMessage().getBody(String.class))
                        .withSubject(clientConfigurations.getSubject())
                        .withTimeToLive(String.valueOf(clientConfigurations.getMessageTtl()));

                response = smnClient.publishMessage(new PublishMessageRequest()
                        .withBody(apiBody)
                        .withTopicUrn(clientConfigurations.getTopicUrn()));
                break;

            case SmnOperations.PUBLISH_AS_TEMPLATED_MESSAGE:
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Publishing as templated message");
                }
                apiBody = new PublishMessageRequestBody()
                        .withMessage(exchange.getMessage().getBody(String.class))
                        .withSubject(clientConfigurations.getSubject())
                        .withTimeToLive(String.valueOf(clientConfigurations.getMessageTtl()))
                        .withMessageTemplateName((String) exchange.getProperty(SmnProperties.TEMPLATE_NAME))
                        .withTags((HashMap<String, String>) exchange.getProperty(SmnProperties.TEMPLATE_TAGS))
                        .withTimeToLive(String.valueOf(clientConfigurations.getMessageTtl()));

                response = smnClient.publishMessage(new PublishMessageRequest()
                        .withBody(apiBody)
                        .withTopicUrn(clientConfigurations.getTopicUrn()));
                break;

            default:
                throw new UnsupportedOperationException(
                        String.format("operation %s not supported in publishMessage service",
                                clientConfigurations.getOperation()));
        }
        setResponseParameters(exchange, response);
    }

    /**
     * maps api response parameters as exchange property
     *
     * @param exchange
     * @param response
     */
    private void setResponseParameters(Exchange exchange, PublishMessageResponse response) {
        if (response == null) {
            return; // mapping is not required if response object is null
        }
        if (!ObjectHelper.isEmpty(response.getMessageId())) {
            exchange.setProperty(SmnProperties.SERVICE_MESSAGE_ID, response.getMessageId());
        }
        if (!ObjectHelper.isEmpty(response.getRequestId())) {
            exchange.setProperty(SmnProperties.SERVICE_REQUEST_ID, response.getRequestId());
        }
    }

    /**
     * validation and initialization of SmnClient object
     *
     * @param simpleNotificationEndpoint
     * @param clientConfigurations
     */
    private void validateAndInitializeSmnClient(
            SimpleNotificationEndpoint simpleNotificationEndpoint,
            ClientConfigurations clientConfigurations) {
        if (simpleNotificationEndpoint.getSmnClient() != null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(
                        "Instance of SmnClient was set on the endpoint. Skipping creation of SmnClient from endpoint parameters");
            }
            this.smnClient = simpleNotificationEndpoint.getSmnClient();
            return;
        }

        //checking for cloud SK (secret key)
        if (ObjectHelper.isEmpty(simpleNotificationEndpoint.getSecretKey()) &&
                ObjectHelper.isEmpty(simpleNotificationEndpoint.getServiceKeys())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("secret key (SK) not found");
            }
            throw new IllegalArgumentException("authentication parameter 'secret key (SK)' not found");
        } else {
            clientConfigurations.setSecretKey(simpleNotificationEndpoint.getSecretKey() != null
                    ? simpleNotificationEndpoint.getSecretKey() : simpleNotificationEndpoint.getServiceKeys().getSecretKey());
        }

        //checking for cloud AK (auth key)
        if (ObjectHelper.isEmpty(simpleNotificationEndpoint.getAccessKey()) &&
                ObjectHelper.isEmpty(simpleNotificationEndpoint.getServiceKeys())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("access key (AK) not found");
            }
            throw new IllegalArgumentException("authentication parameter 'access key (AK)' not found");
        } else {
            clientConfigurations.setAccessKey(simpleNotificationEndpoint.getAccessKey() != null
                    ? simpleNotificationEndpoint.getAccessKey()
                    : simpleNotificationEndpoint.getServiceKeys().getAccessKey());
        }

        //checking for project ID
        if (ObjectHelper.isEmpty(simpleNotificationEndpoint.getProjectId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Project ID not found");
            }
            throw new IllegalArgumentException("project ID not found");
        } else {
            clientConfigurations.setProjectId(simpleNotificationEndpoint.getProjectId());
        }

        //checking for endpoint
        if (StringUtils.isNotEmpty(simpleNotificationEndpoint.getEndpoint())) {
            clientConfigurations.setServiceEndpoint(simpleNotificationEndpoint.getEndpoint());
        } else {
            //checking for region
            String endpointUrl = SimpleNotificationUtils.resolveSmnServiceEndpoint(simpleNotificationEndpoint.getRegion());
            if (endpointUrl == null) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Valid region not found");
                }
                throw new IllegalArgumentException("enter a valid region");
            } else {
                clientConfigurations.setServiceEndpoint(endpointUrl);
            }
        }

        //checking for ignore ssl verification
        boolean ignoreSslVerification = simpleNotificationEndpoint.isIgnoreSslVerification();
        if (ignoreSslVerification) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("SSL verification is ignored. This is unsafe in production environment");
            }
            clientConfigurations.setIgnoreSslVerification(ignoreSslVerification);
        }

        //checking if http proxy authentication is used
        if (simpleNotificationEndpoint.getProxyHost() != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reading http proxy configurations");
            }
            clientConfigurations.setProxyHost(simpleNotificationEndpoint.getProxyHost());
            clientConfigurations.setProxyPort(simpleNotificationEndpoint.getProxyPort());
            clientConfigurations.setProxyUser(simpleNotificationEndpoint.getProxyUser());
            clientConfigurations.setProxyPassword(simpleNotificationEndpoint.getProxyPassword());
        }

        this.smnClient = initializeClient(clientConfigurations);
    }

    /**
     * initialization of smn client. this is lazily initialized on the first message
     *
     * @param  clientConfigurations
     * @return
     */
    private SmnClient initializeClient(ClientConfigurations clientConfigurations) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing Smn client");
        }
        HttpConfig httpConfig
                = HttpConfig.getDefaultHttpConfig().withIgnoreSSLVerification(clientConfigurations.isIgnoreSslVerification());
        if (!StringUtils.isEmpty(clientConfigurations.getProxyHost())) {
            httpConfig.setProxyHost(clientConfigurations.getProxyHost());
            httpConfig.setProxyPort(clientConfigurations.getProxyPort());
            if (!StringUtils.isEmpty(clientConfigurations.getProxyUser())) {
                httpConfig.setProxyUsername(clientConfigurations.getProxyUser());
                httpConfig.setProxyPassword(clientConfigurations.getProxyPassword());
            }
        }

        BasicCredentials credentials = new BasicCredentials()
                .withAk(clientConfigurations.getAccessKey())
                .withSk(clientConfigurations.getSecretKey())
                .withProjectId(clientConfigurations.getProjectId());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Building Smn client");
        }

        // building smn client object
        SmnClient smnClient = SmnClient.newBuilder()
                .withCredential(credentials)
                .withHttpConfig(httpConfig)
                .withEndpoint(clientConfigurations.getServiceEndpoint())
                .build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Successfully initialized Smn client");
        }
        return smnClient;
    }

    /**
     * validation of all user inputs before attempting to invoke a service operation
     *
     * @param simpleNotificationEndpoint
     * @param exchange
     * @param clientConfigurations
     */
    private void validateServiceConfigurations(
            SimpleNotificationEndpoint simpleNotificationEndpoint, Exchange exchange,
            ClientConfigurations clientConfigurations) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Inspecting exchange body");
        }
        // verifying if exchange has valid body content. this is mandatory for 'publish as text' operation
        if (ObjectHelper.isEmpty(exchange.getMessage().getBody())) {
            if (simpleNotificationEndpoint.getOperation().equals("publishAsTextMessage")) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Found null/empty body. Cannot perform publish as text operation");
                }
                throw new IllegalArgumentException("exchange body cannot be null / empty");
            }
        }

        // checking for mandatory field 'operation name'
        if (LOG.isDebugEnabled()) {
            LOG.debug("Inspecting operation name");
        }
        if (ObjectHelper.isEmpty(exchange.getProperty(SmnProperties.SMN_OPERATION))
                && ObjectHelper.isEmpty(simpleNotificationEndpoint.getOperation())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Found null/empty operation name. Cannot proceed with Smn operations");
            }
            throw new IllegalArgumentException("operation name not found");
        } else {
            clientConfigurations.setOperation(exchange.getProperty(SmnProperties.SMN_OPERATION) != null
                    ? (String) exchange.getProperty(SmnProperties.SMN_OPERATION) : simpleNotificationEndpoint.getOperation());
        }

        // checking for mandatory field 'topic name'
        if (LOG.isDebugEnabled()) {
            LOG.debug("Inspecting topic name");
        }
        if (ObjectHelper.isEmpty(exchange.getProperty(SmnProperties.NOTIFICATION_TOPIC_NAME))) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Found null/empty topic name");
            }
            throw new IllegalArgumentException("topic name not found");
        } else {
            clientConfigurations.setTopicUrn(String.format(SmnConstants.TOPIC_URN_FORMAT,
                    simpleNotificationEndpoint.getRegion(), simpleNotificationEndpoint.getProjectId(),
                    exchange.getProperty(SmnProperties.NOTIFICATION_TOPIC_NAME)));
        }

        // checking for optional field 'message subject'
        if (LOG.isDebugEnabled()) {
            LOG.debug("Inspecting notification subject value");
        }
        if (ObjectHelper.isEmpty(exchange.getProperty(SmnProperties.NOTIFICATION_SUBJECT))) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("notification subject not found. defaulting to 'DEFAULT_SUBJECT'");
            }
            clientConfigurations.setSubject("DEFAULT_SUBJECT");
        } else {
            clientConfigurations.setSubject((String) exchange.getProperty(SmnProperties.NOTIFICATION_SUBJECT));
        }

        // checking for optional field 'message ttl'
        if (LOG.isDebugEnabled()) {
            LOG.debug("Inspecting TTL");
        }
        if (ObjectHelper.isEmpty(exchange.getProperty(SmnProperties.NOTIFICATION_TTL))) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("TTL not found. defaulting to default value {}", simpleNotificationEndpoint.getMessageTtl());
            }
            clientConfigurations.setMessageTtl(simpleNotificationEndpoint.getMessageTtl());
        } else {
            clientConfigurations.setMessageTtl((int) exchange.getProperty(SmnProperties.NOTIFICATION_TTL));
        }
    }
}
