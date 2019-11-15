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

import java.util.Arrays;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(ParallelParameterized.class)
public class NotFoundIntegrationTest extends AbstractSalesforceTestBase {

    @Parameter
    public String format;

    @Test
    public void shouldNotReportNotFoundExceptionFromRestApiIfConfiguredNotTo() {
        final Account got = template.requestBody("salesforce:getSObjectWithId?sObjectName=Account&sObjectIdName=Name&format=" + format + "&notFoundBehaviour=NULL", "NonExistant",
                                                 Account.class);

        assertNull("Expecting null when `notFoundBehaviour` is set to NULL", got);
    }

    @Test
    public void shouldReportNotFoundExceptionFromRestApi() {
        try {
            template.requestBody("salesforce:getSObjectWithId?sObjectName=Account&sObjectIdName=Name&format=" + format, "NonExistant", Account.class);
            fail("Expecting CamelExecutionException");
        } catch (final CamelExecutionException e) {
            assertTrue("Expecting the cause of CamelExecutionException to be NoSuchSObjectException", e.getCause() instanceof NoSuchSObjectException);
        }
    }

    @Parameters(name = "{0}")
    public static Iterable<String> formats() {
        return Arrays.asList("XML", "JSON");
    }
}
