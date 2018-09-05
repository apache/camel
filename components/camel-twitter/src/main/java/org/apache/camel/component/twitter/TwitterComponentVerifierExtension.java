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
package org.apache.camel.component.twitter;

import java.util.Map;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import twitter4j.Twitter;
import twitter4j.TwitterException;

public final class TwitterComponentVerifierExtension extends DefaultComponentVerifierExtension {

    public TwitterComponentVerifierExtension() {
        this("twitter");
    }

    public TwitterComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
            .error(ResultErrorHelper.requiresOption("accessToken", parameters))
            .error(ResultErrorHelper.requiresOption("accessTokenSecret", parameters))
            .error(ResultErrorHelper.requiresOption("consumerKey", parameters))
            .error(ResultErrorHelper.requiresOption("consumerSecret", parameters));

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY)
            .error(parameters, this::verifyCredentials)
            .build();
    }

    private void verifyCredentials(ResultBuilder builder, Map<String, Object> parameters) throws Exception {
        try {
            TwitterConfiguration configuration = setProperties(new TwitterConfiguration(), parameters);
            Twitter twitter = configuration.getTwitter();

            twitter.verifyCredentials();
        } catch (TwitterException e) {
            // verifyCredentials throws TwitterException when Twitter service or
            // network is unavailable or if supplied credential is wrong
            ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, e.getErrorMessage())
                .detail("twitter_error_code", e.getErrorCode())
                .detail("twitter_status_code", e.getStatusCode())
                .detail("twitter_exception_code", e.getExceptionCode())
                .detail("twitter_exception_message", e.getMessage())
                .detail("twitter_exception_caused-by-network-issue", e.isCausedByNetworkIssue())
                .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, e.getClass().getName())
                .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, e);

            // For a complete list of error codes see:
            //   https://dev.twitter.com/overview/api/response-codes
            if (e.getErrorCode() == 89) {
                errorBuilder.parameterKey("accessToken");
            }

            builder.error(errorBuilder.build());
        }
    }
}
