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
package org.apache.camel.component.slack;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.slack.api.model.Message;
import com.slack.api.model.block.ImageBlock;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON the producer posts, against a fake webhook and a fake chat.postMessage method.
 */
public class SlackProducerJsonTest extends CamelTestSupport {

    @RegisterExtension
    static AvailablePortFinder.Port PORT = AvailablePortFinder.find();

    private String webhookUrl() {
        return "http://localhost:" + PORT.getPort() + "/slack/webhook";
    }

    private String webhookJson() {
        MockEndpoint webhook = getMockEndpoint("mock:webhook");
        return webhook.getReceivedExchanges().get(0).getIn().getBody(String.class);
    }

    @Test
    public void testWebhookWritesEndpointIconsInSnakeCase() throws Exception {
        getMockEndpoint("mock:webhook").expectedMessageCount(1);

        template.sendBody("slack:#general?username=CamelBot&iconEmoji=:camel:&iconUrl=http://camel/icon.png&webhookUrl="
                          + webhookUrl(),
                "Hello text");

        MockEndpoint.assertIsSatisfied(context);
        String json = webhookJson();
        assertTrue(json.contains("\"text\":\"Hello text\""), json);
        assertTrue(json.contains("\"channel\":\"#general\""), json);
        assertTrue(json.contains("\"username\":\"CamelBot\""), json);
        assertTrue(json.contains("\"icon_emoji\":\":camel:\""), json);
        assertTrue(json.contains("\"icon_url\":\"http://camel/icon.png\""), json);
        assertFalse(json.contains("iconEmoji"), json);
        assertFalse(json.contains("iconUrl"), json);
    }

    @Test
    public void testWebhookWritesMessageInSnakeCaseAndKeepsTheBody() throws Exception {
        getMockEndpoint("mock:webhook").expectedMessageCount(1);

        Message message = new Message();
        message.setText("Hello message");
        message.setThreadTs("1234.5678");
        message.setUnfurlLinks(true);
        message.setBlocks(List.of(ImageBlock.builder().imageUrl("http://camel/image.png").altText("A camel").build()));

        template.sendBody("slack:#general?username=CamelBot&iconEmoji=:camel:&webhookUrl=" + webhookUrl(), message);

        MockEndpoint.assertIsSatisfied(context);
        String json = webhookJson();
        assertTrue(json.contains("\"thread_ts\":\"1234.5678\""), json);
        assertTrue(json.contains("\"unfurl_links\":true"), json);
        assertTrue(json.contains("\"image_url\":\"http://camel/image.png\""), json);
        assertTrue(json.contains("\"alt_text\":\"A camel\""), json);
        assertTrue(json.contains("\"channel\":\"#general\""), json);
        assertTrue(json.contains("\"username\":\"CamelBot\""), json);
        assertTrue(json.contains("\"icon_emoji\":\":camel:\""), json);
        assertFalse(json.contains("threadTs"), json);

        // the caller's body is not changed by the send
        assertNull(message.getChannel());
        assertNull(message.getUsername());
    }

    @Test
    public void testWebhookWritesSlackMessageAttachmentsInSnakeCaseAndKeepsTheBody() throws Exception {
        getMockEndpoint("mock:webhook").expectedMessageCount(1);

        SlackMessage message = createSlackMessageWithAttachment();

        template.sendBody("slack:#general?username=CamelBot&iconEmoji=:camel:&webhookUrl=" + webhookUrl(), message);

        MockEndpoint.assertIsSatisfied(context);
        String json = webhookJson();
        assertTrue(json.contains("\"author_name\":\"Camel\""), json);
        assertTrue(json.contains("\"title_link\":\"http://camel/title\""), json);
        assertTrue(json.contains("\"image_url\":\"http://camel/image.png\""), json);
        assertTrue(json.contains("\"short\":true"), json);
        assertTrue(json.contains("\"icon_emoji\":\":camel:\""), json);
        assertFalse(json.contains("authorName"), json);

        // the caller's body is not changed by the send
        assertNull(message.getChannel());
        assertNull(message.getUsername());
        assertNull(message.getIconEmoji());
    }

    @Test
    public void testTokenModeSendsSlackMessageAttachments() throws Exception {
        MockEndpoint api = getMockEndpoint("mock:api");
        api.expectedMessageCount(1);

        template.sendBody("slack:#general?token=xoxb-test&serverUrl=http://localhost:" + PORT.getPort(),
                createSlackMessageWithAttachment());

        MockEndpoint.assertIsSatisfied(context);
        String form = URLDecoder.decode(api.getReceivedExchanges().get(0).getIn().getBody(String.class),
                StandardCharsets.UTF_8);
        assertTrue(form.contains("text=Hello attachment"), form);
        assertTrue(form.contains("\"author_name\":\"Camel\""), form);
        assertTrue(form.contains("\"title_link\":\"http://camel/title\""), form);
    }

    @Test
    public void testWebhookRuntimeExceptionFailsTheExchangeOnce() throws Exception {
        SlackEndpoint endpoint = context.getEndpoint("slack:#general?webhookUrl=not-a-url", SlackEndpoint.class);
        Producer producer = endpoint.createProducer();
        producer.start();
        try {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody("Hello");
            AtomicInteger done = new AtomicInteger();

            assertDoesNotThrow(() -> AsyncProcessorConverterHelper.convert(producer)
                    .process(exchange, doneSync -> done.incrementAndGet()));

            assertEquals(1, done.get());
            assertNotNull(exchange.getException());
        } finally {
            producer.stop();
        }
    }

    private static SlackMessage createSlackMessageWithAttachment() {
        SlackMessage.Attachment.Field field = new SlackMessage.Attachment.Field();
        field.setTitle("Priority");
        field.setValue("High");
        field.setShortValue(true);

        SlackMessage.Attachment attachment = new SlackMessage.Attachment();
        attachment.setAuthorName("Camel");
        attachment.setTitle("Title");
        attachment.setTitleLink("http://camel/title");
        attachment.setImageUrl("http://camel/image.png");
        attachment.setFields(List.of(field));

        SlackMessage message = new SlackMessage();
        message.setText("Hello attachment");
        message.setAttachments(List.of(attachment));
        return message;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:" + PORT.getPort() + "/slack/webhook")
                        .convertBodyTo(String.class)
                        .to("mock:webhook")
                        .setBody(constant("ok"));

                from("undertow:http://localhost:" + PORT.getPort() + "/api/chat.postMessage")
                        .convertBodyTo(String.class)
                        .to("mock:api")
                        .setBody(constant("{\"ok\": true}"));
            }
        };
    }
}
