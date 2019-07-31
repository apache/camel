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
package org.apache.camel.component.jira;

import java.net.URI;
import java.util.Map;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.ServerInfo;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.component.jira.oauth.JiraOAuthAuthenticationHandler;
import org.apache.camel.component.jira.oauth.OAuthAsynchronousJiraRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jira.JiraConstants.JIRA;
import static org.apache.camel.component.jira.JiraConstants.JIRA_URL;

public class JiraVerifierExtension extends DefaultComponentVerifierExtension {

    private static final Logger LOG = LoggerFactory.getLogger(JiraVerifierExtension.class);

    public JiraVerifierExtension() {
        super(JIRA);
    }

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
            .error(ResultErrorHelper.requiresOption(JIRA_URL, parameters))
            .errors(ResultErrorHelper.requiresAny(parameters,
                OptionsGroup.withName("basic_authentication")
                    .options("username", "password", "!requestToken", "!privateKey", "!consumerKey", "!verificationCode", "!accessToken"),
                OptionsGroup.withName("oauth_authentication")
                    .options("requestToken", "privateKey", "consumerKey", "verificationCode", "accessToken", "!username", "!password")));

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        try {
            JiraConfiguration conf = setProperties(new JiraConfiguration(), parameters);
            OAuthAsynchronousJiraRestClientFactory factory = new OAuthAsynchronousJiraRestClientFactory();

            final URI jiraServerUri = URI.create(conf.getJiraUrl());
            JiraRestClient client;
            if (conf.getUsername() != null) {
                client = factory.createWithBasicHttpAuthentication(jiraServerUri, conf.getUsername(),
                    conf.getPassword());
            } else {
                JiraOAuthAuthenticationHandler oAuthHandler = new JiraOAuthAuthenticationHandler(conf.getConsumerKey(), conf.getVerificationCode(),
                    conf.getPrivateKey(), conf.getAccessToken(), conf.getJiraUrl());
                client = factory.create(jiraServerUri, oAuthHandler);
            }
            // test the connection to the jira server
            ServerInfo serverInfo = client.getMetadataClient().getServerInfo().claim();
            LOG.info("Verify connectivity to jira server OK: {}", serverInfo);

        } catch (RestClientException e) {
            ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, e.getMessage())
                .detail("jira_exception_message", e.getMessage())
                .detail("jira_status_code", e.getStatusCode())
                .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, e.getClass().getName())
                .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, e);

            builder.error(errorBuilder.build());
        } catch (Exception e) {
            ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, e.getMessage())
                .detail("jira_exception_message", e.getMessage())
                .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, e.getClass().getName())
                .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, e);

            builder.error(errorBuilder.build());
        }
        return builder.build();
    }
}
