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
package org.apache.camel.component.stripe.integration;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;

public abstract class StripeIntegrationTestSupport extends CamelTestSupport {

    protected static String apiKey;

    @BeforeAll
    public static void checkApiKey() {
        apiKey = System.getProperty("STRIPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "STRIPE_API_KEY system property is not set. " +
                                            "Run tests with: mvn verify -DSTRIPE_API_KEY=sk_test_...");
        }

        if (!apiKey.startsWith("sk_test_")) {
            throw new IllegalStateException(
                    "STRIPE_API_KEY must be a test key (starting with sk_test_). " +
                                            "Never use live keys for testing!");
        }

        System.setProperty("STRIPE_API_KEY", apiKey);
    }
}
