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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NotFoundManualIT extends AbstractSalesforceTestBase {

    @Test
    public void shouldNotReportNotFoundExceptionFromRestApiIfConfiguredNotTo() {
        final Account got = template.requestBody("salesforce:getSObjectWithId?sObjectName=Account&sObjectIdName=Name"
                                                 + "&notFoundBehaviour=NULL",
                "NonExistent",
                Account.class);

        assertNull(got, "Expecting null when `notFoundBehaviour` is set to NULL");
    }

    @Test
    public void shouldReportNotFoundExceptionFromRestApi() {
        try {
            template.requestBody("salesforce:getSObjectWithId?sObjectName=Account&sObjectIdName=Name",
                    "NonExistant", Account.class);
            fail("Expecting CamelExecutionException");
        } catch (final CamelExecutionException e) {
            assertTrue(e.getCause() instanceof NoSuchSObjectException,
                    "Expecting the cause of CamelExecutionException to be NoSuchSObjectException");
        }
    }
}
