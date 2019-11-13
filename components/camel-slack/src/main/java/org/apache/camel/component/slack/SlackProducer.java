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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

public class SlackProducer extends DefaultProducer {

    private SlackEndpoint slackEndpoint;

    public SlackProducer(SlackEndpoint endpoint) {
        super(endpoint);
        this.slackEndpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        // Create an HttpClient and Post object
        HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
        HttpPost httpPost = new HttpPost(slackEndpoint.getWebhookUrl());

        // Build Helper object
        SlackMessage slackMessage;
        Object payload = exchange.getIn().getBody();
        if (payload instanceof SlackMessage) {
            slackMessage = (SlackMessage) payload;
        } else { 
            slackMessage = new SlackMessage();
            slackMessage.setText(exchange.getIn().getBody(String.class));
        }
        slackMessage.setChannel(slackEndpoint.getChannel());
        slackMessage.setUsername(slackEndpoint.getUsername());
        slackMessage.setIconUrl(slackEndpoint.getIconUrl());
        slackMessage.setIconEmoji(slackEndpoint.getIconEmoji());

        // use charset from exchange or fallback to the default charset
        String charset = ExchangeHelper.getCharsetName(exchange, true);

        // Set the post body
        String json = asJson(slackMessage);
        StringEntity body = new StringEntity(json, charset);

        // Do the post
        httpPost.setEntity(body);

        HttpResponse response = client.execute(httpPost);

        // 2xx is OK, anything else we regard as failure
        if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() > 299) {
            throw new CamelExchangeException("Error POSTing to Slack API: " + response.toString(), exchange);
        }
    }

    /**
     * Returns a JSON string to be posted to the Slack API
     *
     * @return JSON string
     */
    public String asJson(SlackMessage message) {
        Map<String, Object> jsonMap = new HashMap<>();

        // Put the values in a map
        jsonMap.put(SlackConstants.SLACK_TEXT_FIELD, message.getText());
        jsonMap.put(SlackConstants.SLACK_CHANNEL_FIELD, message.getChannel());
        jsonMap.put(SlackConstants.SLACK_USERNAME_FIELD, message.getUsername());
        jsonMap.put(SlackConstants.SLACK_ICON_URL_FIELD, message.getIconUrl());
        jsonMap.put(SlackConstants.SLACK_ICON_EMOJI_FIELD, message.getIconEmoji());

        List<SlackMessage.Attachment> attachments = message.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            buildAttachmentJson(jsonMap, attachments);
        }

        // Return the string based on the JSON Object
        return new JsonObject(jsonMap).toJson();
    }

    private void buildAttachmentJson(Map<String, Object> jsonMap, List<SlackMessage.Attachment> attachments) {
        List<Map<String, Object>> attachmentsJson = new ArrayList<>(attachments.size());
        attachments.forEach(attachment -> {
            Map<String, Object> attachmentJson = new HashMap<>();
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_FALLBACK_FIELD, attachment.getFallback());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_COLOR_FIELD, attachment.getColor());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_PRETEXT_FIELD, attachment.getPretext());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_AUTHOR_NAME_FIELD, attachment.getAuthorName());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_AUTHOR_LINK_FIELD, attachment.getAuthorLink());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_AUTHOR_ICON_FIELD, attachment.getAuthorIcon());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_TITLE_FIELD, attachment.getTitle());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_TITLE_LINK_FIELD, attachment.getTitleLink());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_TEXT_FIELD, attachment.getText());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_IMAGE_URL_FIELD, attachment.getImageUrl());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_FOOTER_FIELD, attachment.getFooter());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_FOOTER_ICON_FIELD, attachment.getFooterIcon());
            attachmentJson.put(SlackConstants.SLACK_ATTACHMENT_TS_FIELD, attachment.getTs());

            List<SlackMessage.Attachment.Field> fields = attachment.getFields();
            if (fields != null && !fields.isEmpty()) {
                buildAttachmentFieldJson(attachmentJson, fields);
            }
            attachmentsJson.add(attachmentJson);
        });
        jsonMap.put(SlackConstants.SLACK_ATTACHMENTS_FIELD, attachmentsJson);
    }

    private void buildAttachmentFieldJson(Map<String, Object> attachmentJson, List<SlackMessage.Attachment.Field> fields) {
        List<Map<String, Object>> fieldsJson = new ArrayList<>(fields.size());
        fields.forEach(field -> {
            Map<String, Object> fieldJson = new HashMap<>();
            fieldJson.put("title", field.getTitle());
            fieldJson.put("value", field.getValue());
            fieldJson.put("short", field.isShortValue());
            fieldsJson.add(fieldJson);
        });
        attachmentJson.put("fields", fieldsJson);
    }
}
