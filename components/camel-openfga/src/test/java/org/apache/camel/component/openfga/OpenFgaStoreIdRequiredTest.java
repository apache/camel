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
package org.apache.camel.component.openfga;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kept apart from {@link OpenFgaEndpointConfigurationTest} on purpose: {@code openFgaClient} is autowired, so a single
 * {@code OpenFgaClient} anywhere in the registry is picked up and the endpoint then has no store id of its own to want.
 * Only a context with no client bound exercises the requirement.
 */
class OpenFgaStoreIdRequiredTest extends CamelTestSupport {

    @Test
    void requiresAStoreIdWhenItBuildsItsOwnClient() {
        assertThatThrownBy(() -> {
            OpenFgaEndpoint endpoint = context.getEndpoint(
                    "openfga:check?user=user:anne&relation=reader&object=document:budget", OpenFgaEndpoint.class);
            endpoint.start();
        }).hasStackTraceContaining("storeId is required");
    }
}
