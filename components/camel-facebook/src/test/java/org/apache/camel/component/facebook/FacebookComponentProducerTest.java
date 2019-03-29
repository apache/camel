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
package org.apache.camel.component.facebook;

import java.lang.reflect.Method;
import java.util.*;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.facebook.data.FacebookMethodsType;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class FacebookComponentProducerTest extends CamelFacebookTestSupport {

    private final Set<String> noArgNames = new HashSet<>();

    private final List<String> idExcludes;
    private final List<String> readingExcludes;

    public FacebookComponentProducerTest() throws Exception {
        for (Class<?> clazz : Facebook.class.getInterfaces()) {
            final String clazzName = clazz.getSimpleName();
            if (clazzName.endsWith("Methods") && !clazzName.equals("GameMethods")) {
                for (Method method : clazz.getDeclaredMethods()) {
                    // find all the no-arg methods
                    if (method.getParameterTypes().length == 0 && FacebookMethodsType.findMethod(method.getName()) != null) {
                        String shortName = getShortName(method.getName());
                        List<String> generalExcludes = Arrays.asList("home", "tabs", "updates", "blocked", "pageSettings", "pageAdmins",
                            "milestones", "offers", "pokes", "promotablePosts", "outbox", "inbox", "notifications");
                        if (!generalExcludes.contains(shortName)) {
                            noArgNames.add(shortName);
                        }
                    }
                }
            }
        }

        idExcludes = new ArrayList<>();
        idExcludes.addAll(Arrays.asList("me", "home", "searchCheckins", "taggableFriends"));
        readingExcludes = new ArrayList<>();
        readingExcludes.addAll(Arrays.asList("pictureURL", "permissions", "taggableFriends", "sSLPictureURL"));

        for (FacebookMethodsType types : FacebookMethodsType.values()) {
            if (types.getArgNames().contains("pageId")) {
                idExcludes.add(getShortName(types.getName()));
                readingExcludes.add(getShortName(types.getName()));
            }
        }
    }

    @Test
    public void testProducers() throws Exception {
        for (String name : noArgNames) {
            MockEndpoint mock = getMockEndpoint("mock:result" + name);
            mock.expectedMinimumMessageCount(1);
            template().sendBody("direct://test" + name, null);

            // with user id
            if (!idExcludes.contains(name)) {
                mock = getMockEndpoint("mock:resultId" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testId" + name, null);
            }

            // with reading
            if (!readingExcludes.contains(name)) {
                mock = getMockEndpoint("mock:resultReading" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testReading" + name, null);
            }

            // with user id and reading
            if (!(idExcludes.contains(name) || readingExcludes.contains(name))) {
                mock = getMockEndpoint("mock:resultIdReading" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testIdReading" + name, null);
            }

            // with user id and reading
            if (!(idExcludes.contains(name) || readingExcludes.contains(name))) {
                mock = getMockEndpoint("mock:resultIdReadingHeader" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testIdReadingHeader" + name, null);
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonStoreEnabled() throws Exception {
        final String rawJSON = template().requestBody("direct://testJsonStoreEnabled", new String[]{"me"}, String.class);
        assertNotNull("NULL rawJSON", rawJSON);
        assertFalse("Empty rawJSON", rawJSON.isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // Deprecated exceptions are ignored in the tests since this depends on the
                // configuration and/or creation date of the Facebook application.
                onException(FacebookException.class).setHandledPolicy(new IgnoreDeprecatedExceptions());
                //---------------
                // producer tests
                //---------------
                // generate test routes for all methods with no args
                for (String name : noArgNames) {
                    from("direct://test" + name)
                        .setHeader("mock", constant("mock:result" + name))
                        .to("facebook://" + name + "?" + getOauthParams())
                        .to("mock:result" + name);

                    // with user id
                    if (!idExcludes.contains(name)) {
                        from("direct://testId" + name)
                            .setHeader("mock", constant("mock:resultId" + name))
                            .to("facebook://" + name + "?userId=me&" + getOauthParams())
                            .to("mock:resultId" + name);
                    }

                    // reading options
                    if (!readingExcludes.contains(name)) {
                        from("direct://testReading" + name)
                            .setHeader("mock", constant("mock:resultReading" + name))
                            .to("facebook://" + name + "?reading.limit=10&reading.locale=en,US&" + getOauthParams())
                            .to("mock:resultReading" + name);
                    }

                    // with id and reading options
                    if (!(idExcludes.contains(name) || readingExcludes.contains(name))) {
                        from("direct://testIdReading" + name)
                            .setHeader("mock", constant("mock:resultIdReading" + name))
                            .to("facebook://" + name + "?userId=me&reading.limit=10&reading.locale=en,US&" + getOauthParams())
                            .to("mock:resultIdReading" + name);
                    }

                    // with id and reading options
                    if (!(idExcludes.contains(name) || readingExcludes.contains(name))) {
                        from("direct://testIdReadingHeader" + name)
                            .setHeader("mock", constant("mock:resultIdReadingHeader" + name))
                            .setHeader("CamelFacebook.reading.limit", constant("10"))
                            .to("facebook://" + name + "?userId=me&reading.locale=en,US&" + getOauthParams())
                            .to("mock:resultIdReadingHeader" + name);
                    }
                }

                from("direct://testJsonStoreEnabled")
                    .to("facebook://users?inBody=ids&jsonStoreEnabled=true&" + getOauthParams())
                    .setBody(simple("header." + FacebookConstants.RAW_JSON_HEADER));

                // TODO add tests for the rest of the supported methods
            }
        };
    }

    private class IgnoreDeprecatedExceptions implements Predicate {
        @Override
        public boolean matches(Exchange exchange) {
            RuntimeCamelException camelException = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, RuntimeCamelException.class);
            if (camelException != null
                && camelException.getCause() != null
                && camelException.getCause() instanceof FacebookException) {
                FacebookException facebookException = (FacebookException) camelException.getCause();
                if (facebookException.getErrorCode() == 11 || facebookException.getErrorCode() == 12) {
                    getMockEndpoint(exchange.getIn().getHeader("mock", String.class)).expectedMinimumMessageCount(0);

                    return true;
                }
            }
            return false;
        }
    }
}
