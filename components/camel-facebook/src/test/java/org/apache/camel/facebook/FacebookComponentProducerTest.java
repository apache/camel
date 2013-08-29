package org.apache.camel.facebook;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import facebook4j.Facebook;

public class FacebookComponentProducerTest extends CamelFacebookTestSupport {

    private final Set<String> noArgNames = new HashSet<String>();

    private final List<String> idExcludes;
    private final List<String> readingExcludes;

    public FacebookComponentProducerTest() throws Exception {
        for (Class clazz : Facebook.class.getInterfaces()) {
            final String clazzName = clazz.getSimpleName();
            if (clazzName.endsWith("Methods") && !clazzName.equals("GameMethods")) {
                for (Method method : clazz.getDeclaredMethods()) {
                    String name = method.getName();
                    if (name.startsWith("get")) {
                        name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    }
                    if (name.startsWith("search") && !"search".equals(name)) {
                        name = Character.toLowerCase(name.charAt(6)) + name.substring(7);
                    }
                    // find all the no-arg methods
                    if (method.getParameterTypes().length == 0) {
                        noArgNames.add(name);
                    }
                }
            }
        }

        idExcludes = Arrays.asList(new String[] { "me", "home", "searchCheckins" });
        readingExcludes = Arrays.asList(new String[] { "pictureURL", "permissions" });
    }

    @Test
    public void testProducers() throws Exception {
        for (String name : noArgNames) {
            MockEndpoint mock = getMockEndpoint("mock:result" + name);
            mock.expectedMinimumMessageCount(1);
            template().sendBody("direct://test" + name, null);
            // avoid hitting Facebook API call rate limit
            Thread.sleep(1000);

            // with user id
            if (!idExcludes.contains(name)) {
                mock = getMockEndpoint("mock:resultId" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testId" + name, null);
                // avoid hitting Facebook API call rate limit
                Thread.sleep(1000);
            }

            // with reading
            if (!readingExcludes.contains(name)) {
                mock = getMockEndpoint("mock:resultReading" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testReading" + name, null);
                // avoid hitting Facebook API call rate limit
                Thread.sleep(1000);
            }

            // with user id and reading
            if (!(idExcludes.contains(name) || readingExcludes.contains(name))) {
                mock = getMockEndpoint("mock:resultIdReading" + name);
                mock.expectedMinimumMessageCount(1);
                template().sendBody("direct://testIdReading" + name, null);
                // avoid hitting Facebook API call rate limit
                Thread.sleep(1000);
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
