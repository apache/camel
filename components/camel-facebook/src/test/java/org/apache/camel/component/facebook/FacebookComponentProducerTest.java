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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import facebook4j.Facebook;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class FacebookComponentProducerTest extends CamelFacebookTestSupport {

    private final Set<String> noArgNames = new HashSet<String>();

    private final List<String> idExcludes;
    private final List<String> readingExcludes;

    public FacebookComponentProducerTest() throws Exception {
        for (Class<?> clazz : Facebook.class.getInterfaces()) {
            final String clazzName = clazz.getSimpleName();
            if (clazzName.endsWith("Methods") && !clazzName.equals("GameMethods")) {
                for (Method method : clazz.getDeclaredMethods()) {
                    // find all the no-arg methods
                    if (method.getParameterTypes().length == 0) {
                        noArgNames.add(getShortName(method.getName()));
                    }
                }
            }
        }

        idExcludes = Arrays.asList("me", "home", "searchCheckins");
        readingExcludes = Arrays.asList("pictureURL", "permissions");
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
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                //---------------
                // producer tests
                //---------------
                // generate test routes for all methods with no args
                for (String name : noArgNames) {
                    from("direct://test" + name)
                        .to("facebook://" + name + "?" + getOauthParams())
                        .to("mock:result" + name);

                    // with user id
                    if (!idExcludes.contains(name)) {
                        from("direct://testId" + name)
                            .to("facebook://" + name + "?userId=me&" + getOauthParams())
                            .to("mock:resultId" + name);
                    }

                    // reading options
                    if (!readingExcludes.contains(name)) {
                        from("direct://testReading" + name)
                            .to("facebook://" + name + "?reading.limit=10&reading.locale=en,US&" + getOauthParams())
                            .to("mock:resultReading" + name);
                    }

                    // with id and reading options
                    if (!(idExcludes.contains(name) || readingExcludes.contains(name))) {
                        from("direct://testIdReading" + name)
                            .to("facebook://" + name + "?userId=me&reading.limit=10&reading.locale=en,US&" + getOauthParams())
                            .to("mock:resultIdReading" + name);
                    }
                }

                // TODO add tests for the rest of the supported methods
            }
        };
    }

}
