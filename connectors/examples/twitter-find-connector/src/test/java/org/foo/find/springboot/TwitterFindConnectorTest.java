package org.foo.find.springboot;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twitter.TwitterEndpointPolling;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        TwitterFindConnectorTest.TestConfiguration.class
    }
)
public class TwitterFindConnectorTest {
    @Autowired
    private CamelContext camelContext;

    @Test
    public void testConfiguration() throws Exception {
        TwitterEndpointPolling twitterEnpoint = null;

        for (Endpoint endpoint : camelContext.getEndpoints()) {
            if (endpoint instanceof TwitterEndpointPolling) {
                twitterEnpoint = (TwitterEndpointPolling)endpoint;
                break;
            }
        }

        Assert.assertNotNull("No TwitterConsumerPolling found", twitterEnpoint);
        Assert.assertTrue(twitterEnpoint.getEndpointUri().startsWith("twitter-find-component:"));
        Assert.assertEquals("camelsearchtest", twitterEnpoint.getKeywords());
        Assert.assertFalse(twitterEnpoint.isFilterOld());
    }

    // ***********************************
    // Configuration
    // ***********************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("tw-find?keywords=camelsearchtest&filterOld=false")
                        .noAutoStartup()
                        .to("mock:result");
                }
            };
        }
    }
}
