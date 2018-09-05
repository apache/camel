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
package org.apache.camel.component.salesforce;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.NoSuchOptionException;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;

public class SalesforceComponentVerifierExtension extends DefaultComponentVerifierExtension {

    SalesforceComponentVerifierExtension() {
        super("salesforce");
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // Validate mandatory component options, needed to be done here as these
        // options are not properly marked as mandatory in the catalog.
        //
        // Validation rules are borrowed from SalesforceLoginConfig's validate
        // method, which support 3 workflow:
        //
        // - OAuth Username/Password Flow
        // - OAuth Refresh Token Flow:
        // - OAuth JWT Flow
        //
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
            .errors(ResultErrorHelper.requiresAny(parameters,
                OptionsGroup.withName(AuthenticationType.USERNAME_PASSWORD)
                    .options("clientId", "clientSecret", "userName", "password", "!refreshToken", "!keystore"),
                OptionsGroup.withName(AuthenticationType.REFRESH_TOKEN)
                    .options("clientId", "clientSecret", "refreshToken", "!password", "!keystore"),
                OptionsGroup.withName(AuthenticationType.JWT)
                    .options("clientId", "userName", "keystore", "!password", "!refreshToken")));

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        // Default is success
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        try {
            SalesforceClientTemplate.invoke(getCamelContext(), parameters, client -> {
                client.getVersions(Collections.emptyMap(),
                    (response, headers, exception) ->  processSalesforceException(builder, Optional.ofNullable(exception)));
                return null;
            });
        } catch (NoSuchOptionException e) {
            builder.error(
                ResultErrorBuilder.withMissingOption(e.getOptionName()).build()
            );
        } catch (SalesforceException e) {
            processSalesforceException(builder, Optional.of(e));
        } catch (Exception e) {
            builder.error(
                ResultErrorBuilder.withException(e).build()
            );
        }

        return builder.build();
    }

    // *********************************
    // Helpers
    // *********************************

    private static void processSalesforceException(ResultBuilder builder, Optional<SalesforceException> exception) {
        exception.ifPresent(e -> {
            builder.error(
                ResultErrorBuilder.withException(e)
                    .detail(VerificationError.HttpAttribute.HTTP_CODE, e.getStatusCode())
                    .build()
            );

            for (RestError error : e.getErrors()) {
                builder.error(
                    ResultErrorBuilder.withCode(VerificationError.StandardCode.GENERIC)
                        .description(error.getMessage())
                        .parameterKeys(error.getFields())
                        .detail("salesforce_code", error.getErrorCode())
                        .build()
                );
            }
        });
    }

}
