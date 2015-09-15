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
package org.apache.camel.component.facebook;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import facebook4j.FacebookException;
import facebook4j.api.SearchMethods;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultPollingConsumerPollStrategy;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.junit.Test;

public class FacebookComponentConsumerTest extends CamelFacebookTestSupport {
    public static final String APACHE_FOUNDATION_PAGE_ID = "6538157161";

    private final Set<String> searchNames = new HashSet<String>();
    private List<String> excludedNames;

    public FacebookComponentConsumerTest() throws Exception {
        // find search methods for consumer tests
        for (Method method : SearchMethods.class.getDeclaredMethods()) {
            String name = getShortName(method.getName());
            if (!"locations".equals(name) && !"checkins".equals(name)) {
                searchNames.add(name);
            }
        }

        excludedNames = Arrays.asList("places", "users", "search", "pages", "searchPosts");
    }

    @Test
    public void testConsumers() throws InterruptedException {
        for (String name : searchNames) {
            MockEndpoint mock;
            if (!excludedNames.contains(name)) {
                mock = getMockEndpoint("mock:consumeResult" + name);
                mock.expectedMinimumMessageCount(1);
            }

            mock = getMockEndpoint("mock:consumeQueryResult" + name);
            mock.expectedMinimumMessageCount(1);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonStoreEnabled() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:testJsonStoreEnabled");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        final String rawJSON = mock.getExchanges().get(0).getIn().getHeader(FacebookConstants.RAW_JSON_HEADER, String.class);
        assertNotNull("Null rawJSON", rawJSON);
        assertFalse("Empty rawJSON", rawJSON.isEmpty());
    }

    @Test
    public void testPage() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:testPage");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
    }

    @Override
    protected void doPostSetup() throws Exception {
        ignoreDeprecatedApiError();
    }

    private void ignoreDeprecatedApiError() {
        for (final Route route : context().getRoutes()) {
            ((ScheduledPollConsumer)route.getConsumer()).setPollStrategy(new DefaultPollingConsumerPollStrategy() {
                @Override
                public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
                    if (e.getCause() instanceof FacebookException) {
                        FacebookException facebookException = (FacebookException) e.getCause();
                        if (facebookException.getErrorCode() == 11 || facebookException.getErrorCode() == 12 || facebookException.getErrorCode() == 1) {
                            context().stopRoute(route.getId());
                            String method = ((FacebookEndpoint) route.getEndpoint()).getMethod();
                            MockEndpoint mock = getMockEndpoint("mock:consumeQueryResult" + method);
                            mock.expectedMinimumMessageCount(0);
                            MockEndpoint mock2 = getMockEndpoint("mock:consumeResult" + method);
                            mock2.expectedMinimumMessageCount(0);
                            log.warn("Ignoring failed Facebook deprecated API call", facebookException);
                        }
                    }
                    return super.rollback(consumer, endpoint, retryCounter, e);
                }
            });
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // start with a 30 day window for the first delayed poll
                String since = "RAW(" + new SimpleDateFormat(FacebookConstants.FACEBOOK_DATE_FORMAT).format(
                    new Date(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS))) + ")";

                for (String name : searchNames) {
                    if (!excludedNames.contains(name)) {
                        // consumer.sendEmptyMessageWhenIdle is true since user may not have some items like events
                        from("facebook://" + name + "?reading.limit=10&reading.locale=en.US&reading.since="
                            + since + "&consumer.initialDelay=1000&consumer.sendEmptyMessageWhenIdle=true&"
                            + getOauthParams())
                            .to("mock:consumeResult" + name);
                    }

                    from("facebook://" + name + "?query=cheese&reading.limit=10&reading.locale=en.US&reading.since="
                        + since + "&consumer.initialDelay=1000&" + getOauthParams())
                        .to("mock:consumeQueryResult" + name);
                }

                from("facebook://me?jsonStoreEnabled=true&" + getOauthParams())
                    .to("mock:testJsonStoreEnabled");

                // test unix timestamp support
                long unixSince =  TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    - TimeUnit.SECONDS.convert(30, TimeUnit.DAYS);
                from("facebook://page?pageId=" + APACHE_FOUNDATION_PAGE_ID + "&reading.limit=10&reading.since=" + unixSince + "&" + getOauthParams())
                        .to("mock:testPage");

                // TODO add tests for the rest of the supported methods
            }
        };
    }

}
