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
package org.apache.camel.component.google.speechtotext;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;

public final class GoogleCloudSpeechToTextClientFactory {

    private GoogleCloudSpeechToTextClientFactory() {
    }

    public static SpeechClient create(
            CamelContext context,
            GoogleCloudSpeechToTextConfiguration configuration)
            throws Exception {

        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, configuration);

        if (credentials != null) {
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            return SpeechClient.create(settings);
        } else {
            SpeechSettings settings = SpeechSettings.newBuilder().build();
            return SpeechClient.create(settings);
        }
    }
}
