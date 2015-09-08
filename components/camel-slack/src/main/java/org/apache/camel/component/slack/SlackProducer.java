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
package org.apache.camel.component.slack;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;

public class SlackProducer extends DefaultProducer {

    private SlackEndpoint slackEndpoint;

    public SlackProducer(SlackEndpoint endpoint) {
        super(endpoint);
        this.slackEndpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        // Create an HttpClient and Post object
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(slackEndpoint.getWebhookUrl());

        // Build Helper object
        SlackMessage slackMessage = new SlackMessage();
        slackMessage.setText(exchange.getIn().getBody(String.class));
        slackMessage.setChannel(slackEndpoint.getChannel());
        slackMessage.setUsername(slackEndpoint.getUsername());
        slackMessage.setIconUrl(slackEndpoint.getIconUrl());
        slackMessage.setIconEmoji(slackEndpoint.getIconEmoji());

        // use charset from exchange or fallback to the default charset
        String charset = IOHelper.getCharsetName(exchange, true);

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
        Map<String, String> jsonMap = new HashMap<String, String>();

        // Put the values in a map
        jsonMap.put("text", message.getText());
        jsonMap.put("channel", message.getChannel());
        jsonMap.put("username", message.getUsername());
        jsonMap.put("icon_url", message.getIconUrl());
        jsonMap.put("icon_emoji", message.getIconEmoji());

        // Generate a JSONObject
        JSONObject jsonObject = new JSONObject(jsonMap);

        // Return the string based on the JSON Object
        return JSONObject.toJSONString(jsonObject);
    }

}
