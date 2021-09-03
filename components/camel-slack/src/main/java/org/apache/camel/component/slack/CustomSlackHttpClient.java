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

import java.io.IOException;

import com.slack.api.SlackConfig;
import com.slack.api.util.http.SlackHttpClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Slack-api-client use the OkHttpClient v4.x.x We need to override the SlackHttpClient to force the function
 * postJsonBody to work with the v3.x.x
 */
public class CustomSlackHttpClient extends SlackHttpClient {

    private static final MediaType MEDIA_TYPE_APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");

    private final SlackConfig config = SlackConfig.DEFAULT;
    private final OkHttpClient okHttpClient = buildOkHttpClient(config);

    @Override
    public Response postJsonBody(String url, Object obj) throws IOException {
        RequestBody body = RequestBody.create(MEDIA_TYPE_APPLICATION_JSON, (String) obj);
        Request request = new Request.Builder().url(url).post(body).build();
        return okHttpClient.newCall(request).execute();
    }
}
