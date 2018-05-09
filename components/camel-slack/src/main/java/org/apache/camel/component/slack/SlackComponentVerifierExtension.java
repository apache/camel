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

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;

public class SlackComponentVerifierExtension extends DefaultComponentVerifierExtension {

    public SlackComponentVerifierExtension() {
        this("slack");
    }

    public SlackComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    // *********************************
    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .error(ResultErrorHelper.requiresOption("webhookUrl", parameters));

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************
    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY)
                .error(parameters, this::verifyCredentials).build();
    }

    private void verifyCredentials(ResultBuilder builder, Map<String, Object> parameters) {

        String webhookUrl = (String) parameters.get("webhookUrl");

        try {
            HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
            HttpPost httpPost = new HttpPost(webhookUrl);

            // Build Helper object
            SlackMessage slackMessage;
            slackMessage = new SlackMessage();
            slackMessage.setText("Test connection");

            // Set the post body
            String json = asJson(slackMessage);
            StringEntity body = new StringEntity(json);

            // Do the post
            httpPost.setEntity(body);

            HttpResponse response = client.execute(httpPost);

            // 2xx is OK, anything else we regard as failure
            if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() > 299) {
                builder.error(ResultErrorBuilder
                        .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION,
                                "Invalid webhookUrl")
                        .parameterKey("webhookUrl").build());
            }
        } catch (Exception e) {
            builder.error(ResultErrorBuilder
                    .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION,
                            "Invalid webhookUrl")
                    .parameterKey("webhookUrl").build());
        }
    }

    protected String asJson(SlackMessage message) {
        Map<String, Object> jsonMap = new HashMap<>();

        // Put the values in a map
        jsonMap.put("text", message.getText());

        // Generate a JSONObject
        JSONObject jsonObject = new JSONObject(jsonMap);

        // Return the string based on the JSON Object
        return JSONObject.toJSONString(jsonObject);
    }

}