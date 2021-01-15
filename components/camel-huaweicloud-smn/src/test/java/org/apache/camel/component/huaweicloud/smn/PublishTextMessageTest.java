package org.apache.camel.component.huaweicloud.smn;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

public class PublishTextMessageTest extends CamelTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublishTextMessageTest.class.getName());
    private static int wiremockServerPort = 8080;

    TestConfiguration testConfiguration = new TestConfiguration();

    WireMockServer wireMockServer;

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:publish_text_message")
                        .setProperty("CamelHwCloudSmnSubject", constant("Dummy Subject Line"))
                        .setProperty("CamelHwCloudSmnTopic", constant(testConfiguration.getProperty("topic")))
                        .setProperty("CamelHwCloudSmnMessageTtl", constant(60))
                        //.to("hwcloud-smn:publishMessageService?operation=publishAsTextMessage&authKey="+testConfiguration.getProperty("authKey")+"&secretKey="+testConfiguration.getProperty("secretKey")+"&projectId="+testConfiguration.getProperty("projectId")+"&region="+testConfiguration.getProperty("region")+"&proxyHost=localhost&proxyPort=3128&ignoreSslVerification=true")
                        .to("hwcloud-smn:publishMessageService?operation=publishAsTextMessage&authKey="+testConfiguration.getProperty("authKey")+"&secretKey="+testConfiguration.getProperty("secretKey")+"&projectId="+testConfiguration.getProperty("projectId")+"&region="+testConfiguration.getProperty("region")+"&ignoreSslVerification=true")
                        .log("publish message successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:publish_text_message_result");
            }
        };
    }

    @Test
    public void testTextNotificationSend() throws Exception {

        boolean isMockedServerTest = testConfiguration.getProperty("region").equals("unit-test");
        if(isMockedServerTest) {
            LOGGER.info("region is unit-test. Starting up wiremock stubs");
            initWireMock();
            setupSimpleNotificationsUtilsMock();
        }

        MockEndpoint mock = getMockEndpoint("mock:publish_text_message_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:publish_text_message", "message content");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        Assert.assertNotNull(responseExchange.getProperty("CamelSmnMesssageId"));
        Assert.assertNotNull(responseExchange.getProperty("CamelSmnRequestId"));
        Assert.assertTrue(responseExchange.getProperty("CamelSmnMesssageId").toString().length() > 0);
        Assert.assertTrue(responseExchange.getProperty("CamelSmnRequestId").toString().length() > 0);


        if(isMockedServerTest) {
            Assert.assertEquals("bf94b63a5dfb475994d3ac34664e24f2", responseExchange.getProperty("CamelSmnMesssageId"));
            Assert.assertEquals("6a63a18b8bab40ffb71ebd9cb80d0085", responseExchange.getProperty("CamelSmnRequestId"));

            LoggedRequest loggedRequest = TestUtils.retrieveTextNotificationRequest(getAllServeEvents());;
            LOGGER.info("Verifying wiremock request");
            Assert.assertEquals("http://localhost:8080/v2/9071a38e7f6a4ba7b7bcbeb7d4ea6efc/notifications/topics/urn:smn:unit-test:9071a38e7f6a4ba7b7bcbeb7d4ea6efc:reji-test/publish", loggedRequest.getAbsoluteUrl());
            Assert.assertEquals("eyJzdWJqZWN0IjoiRHVtbXkgU3ViamVjdCBMaW5lIiwibWVzc2FnZSI6Im1lc3NhZ2UgY29udGVudCIsInRpbWVfdG9fbGl2ZSI6IjYwIn0=", loggedRequest.getBodyAsBase64());
            Assert.assertEquals("{\"subject\":\"Dummy Subject Line\",\"message\":\"message content\",\"time_to_live\":\"60\"}", loggedRequest.getBodyAsString());
        }
    }


    private void setupSimpleNotificationsUtilsMock() {
        Mockito.reset();
        try {
            Mockito.mockStatic(SimpleNotificationUtils.class);
            Mockito.when(SimpleNotificationUtils.resolveSmnServiceEndpoint("unit-test")).thenReturn("http://localhost:" + wiremockServerPort);
        }catch (MockitoException e) {
            LOGGER.info("Mock already registered. Using existing registration");
        }
    }

    private void initWireMock() throws Exception {
       try {
            wireMockServer = new WireMockServer(wiremockServerPort);
            wireMockServer.start();
        }catch (Exception e) {
           LOGGER.info("wiremock server already registered in test context. using the same");
        }

        wireMockServer.stubFor(post(urlPathMatching("/v2/9071a38e7f6a4ba7b7bcbeb7d4ea6efc/notifications/topics/urn:smn:unit-test:9071a38e7f6a4ba7b7bcbeb7d4ea6efc:reji-test/publish"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Files.readAllBytes(Paths.get("src/test/resources/templates/smn_response.json"))).withStatus(200)));
        LOGGER.info("Wiremock started...");
    }
}
