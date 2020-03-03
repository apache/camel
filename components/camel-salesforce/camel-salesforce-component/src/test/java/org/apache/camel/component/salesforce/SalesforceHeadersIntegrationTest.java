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
package org.apache.camel.component.salesforce;

import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class SalesforceHeadersIntegrationTest extends AbstractSalesforceTestBase {

    @Test
    public void shouldSendCustomHeaders() {
        final Exchange exchange = template().request("salesforce:getGlobalObjects", (Processor)exchange1 -> {
            exchange1.getIn().setHeader("Sforce-Limit-Info", Collections.singletonList("api-usage"));
        });

        Assertions.assertThat(exchange.getMessage().getBody(GlobalObjects.class)).isNotNull();
        Assertions.assertThat(exchange.getMessage().getHeader("Sforce-Limit-Info", String.class)).contains("api-usage=");
    }
}
