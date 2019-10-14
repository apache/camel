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
package org.apache.camel.support;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class EndpointHelperTest {

    @Test
    public void matchEndpointsShouldIgnoreQueryParamOrder() {
        String endpointUri = "sjms:queue:my-queue?transacted=true&consumerCount=1";
        String endpointUriShuffled = "sjms:queue:my-queue?consumerCount=1&transacted=true";
        String notMatchingEndpointUri = "sjms:queue:my-queue?consumerCount=1";

        assertThat(EndpointHelper.matchEndpoint(null, endpointUri, endpointUri), is(true));
        assertThat(EndpointHelper.matchEndpoint(null, endpointUri, endpointUriShuffled), is(true));
        assertThat(EndpointHelper.matchEndpoint(null, endpointUriShuffled, endpointUri), is(true));
        assertThat(EndpointHelper.matchEndpoint(null, endpointUriShuffled, endpointUriShuffled), is(true));
        assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, endpointUriShuffled), is(false));
        assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, endpointUri), is(false));
        assertThat(EndpointHelper.matchEndpoint(null, endpointUri, notMatchingEndpointUri), is(false));
        assertThat(EndpointHelper.matchEndpoint(null, endpointUriShuffled, notMatchingEndpointUri), is(false));
    }

    @Test
    public void matchEndpointsShouldMatchWildcards() {
        String endpointUri = "sjms:queue:my-queue?transacted=true&consumerCount=1";
        String notMatchingEndpointUri = "sjms:queue:my-queue";
        String pattern = "sjms:queue:my-queue?*";

        assertThat(EndpointHelper.matchEndpoint(null, endpointUri, pattern), is(true));
        assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, pattern), is(false));
    }

    @Test
    public void matchEndpointsShouldMatchRegex() {
        String endpointUri = "sjms:queue:my-queue?transacted=true&consumerCount=1";
        String notMatchingEndpointUri = "sjms:queue:my-queue?transacted=false&consumerCount=1";
        String pattern = "sjms://.*transacted=true.*";

        assertThat(EndpointHelper.matchEndpoint(null, endpointUri, pattern), is(true));
        assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, pattern), is(false));
    }

}
