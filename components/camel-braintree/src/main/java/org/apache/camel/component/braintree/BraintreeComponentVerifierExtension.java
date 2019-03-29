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
package org.apache.camel.component.braintree;

import java.util.Map;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;

public class BraintreeComponentVerifierExtension extends DefaultComponentVerifierExtension {

    BraintreeComponentVerifierExtension() {
        super("braintree");
    }

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // Validate mandatory component options, needed to be done here as these
        // options are not properly marked as mandatory in the catalog.
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .errors(ResultErrorHelper.requiresAny(parameters,
                        OptionsGroup.withName(AuthenticationType.PUBLIC_PRIVATE_KEYS)
                                .options("environment", "merchantId", "publicKey", "privateKey", "!accessToken"),
                        OptionsGroup.withName(AuthenticationType.ACCESS_TOKEN)
                                .options("!environment", "!merchantId", "!publicKey", "!privateKey", "accessToken")));

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }
}
