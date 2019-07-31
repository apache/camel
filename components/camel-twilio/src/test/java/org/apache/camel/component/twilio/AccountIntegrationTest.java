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
package org.apache.camel.component.twilio;

import java.util.HashMap;
import java.util.Map;

import com.twilio.base.ResourceSet;
import com.twilio.rest.api.v2010.Account;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twilio.internal.AccountApiMethod;
import org.apache.camel.component.twilio.internal.TwilioApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.twilio.rest.api.v2010.Account} APIs.
 */
public class AccountIntegrationTest extends AbstractTwilioTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AccountIntegrationTest.class);
    private static final String PATH_PREFIX = TwilioApiCollection.getCollection().getApiName(AccountApiMethod.class).getName();

    @Test
    public void testFetcher() throws Exception {
        final Account result = requestBody("direct://FETCHER", null);

        assertNotNull("fetcher result not null", result);
        assertNotNull("fetcher result sid not null", result.getSid());
        LOG.debug("fetcher: " + result);
    }

    @Test
    public void testFetcherWithPathSid() throws Exception {
        final Account result = requestBodyAndHeaders("direct://FETCHER", null,
            headers("CamelTwilioPathSid", ((TwilioComponent) context().getComponent("twilio")).getAccountSid()));

        assertNotNull("fetcher result not null", result);
        assertNotNull("fetcher result sid not null", result.getSid());
        LOG.debug("fetcher: " + result);
    }

    @Test
    public void testReader() throws Exception {
        final ResourceSet<Account> result = requestBody("direct://READER", null);

        assertNotNull("reader result not null", result);
        result.forEach(account -> {
            assertNotNull("reader result account not null", account);
            LOG.debug("reader: " + account);
        });
    }

    @Test
    public void testReaderWithStatusActive() throws Exception {
        final ResourceSet<Account> result = requestBodyAndHeaders("direct://READER", null,
            headers("CamelTwilioStatus", "active"));

        assertNotNull("reader result not null", result);
        result.forEach(account -> {
            assertEquals("reader result account active", Account.Status.ACTIVE, account.getStatus());
            LOG.debug("reader: " + account);
        });
    }

    private static Map<String, Object> headers(String name, Object value) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(name, value);
        return headers;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for fetcher
                from("direct://FETCHER")
                    .to("twilio://" + PATH_PREFIX + "/fetch");

                // test route for reader
                from("direct://READER")
                    .to("twilio://" + PATH_PREFIX + "/read");

            }
        };
    }
}
