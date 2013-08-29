package org.apache.camel.facebook;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import facebook4j.api.SearchMethods;

public class FacebookComponentConsumerTest extends CamelFacebookTestSupport {

    private final Set<String> searchNames = new HashSet<String>();

    public FacebookComponentConsumerTest() throws Exception {
        // find search methods for consumer tests
        for (Method method : SearchMethods.class.getDeclaredMethods()) {
            String name = method.getName();
            if (name.startsWith("search") && !"search".equals(name)) {
                name = Character.toLowerCase(name.charAt(6)) + name.substring(7);
            }
            if (!"locations".equals(name) && !"checkins".equals(name)) {
                searchNames.add(name);
            }
        }
    }

    @Test
    public void testConsumers() throws InterruptedException {
        for (String name : searchNames) {
            MockEndpoint mock = getMockEndpoint("mock:consumeResult" + name);
            mock.expectedMinimumMessageCount(1);
        }
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                // start with a 7 day window for the first delayed poll
                String since = new SimpleDateFormat(FacebookConstants.FACEBOOK_DATE_FORMAT).format(
                    new Date(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)));

                for (String name : searchNames) {
                    from("facebook://" + name + "?query=cheese&reading.limit=10&reading.locale=en.US&reading.since="
                        + since + "&consumer.initialDelay=1000&" + getOauthParams())
                        .to("mock:consumeResult" + name);
                }

                // TODO add tests for the rest of the supported methods
            }
        };
    }

}
