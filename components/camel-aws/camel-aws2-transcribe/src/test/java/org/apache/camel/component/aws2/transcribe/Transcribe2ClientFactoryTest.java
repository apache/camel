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

package org.apache.camel.component.aws2.transcribe;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

public class Transcribe2ClientFactoryTest {

    @Test
    public void getStandardTranscribeClientIsNotNull() {
        Transcribe2Configuration transcribe2Configuration = new Transcribe2Configuration();
        transcribe2Configuration.setAccessKey("accesskey");
        transcribe2Configuration.setSecretKey("secretkey");
        transcribe2Configuration.setRegion("eu-west-1");
        TranscribeClient transcribeClient = Transcribe2ClientFactory.getTranscribeClient(transcribe2Configuration);
        assertNotNull(transcribeClient);
    }

    @Test
    public void getIAMOptimizedTranscribeClientIsNotNull() {
        Transcribe2Configuration transcribe2Configuration = new Transcribe2Configuration();
        transcribe2Configuration.setUseDefaultCredentialsProvider(true);
        transcribe2Configuration.setRegion("eu-west-1");
        TranscribeClient transcribeClient = Transcribe2ClientFactory.getTranscribeClient(transcribe2Configuration);
        assertNotNull(transcribeClient);
    }

    @Test
    public void getIAMProfileOptimizedTranscribeClientIsNotNull() {
        Transcribe2Configuration transcribe2Configuration = new Transcribe2Configuration();
        transcribe2Configuration.setUseProfileCredentialsProvider(true);
        transcribe2Configuration.setProfileCredentialsName("default");
        transcribe2Configuration.setRegion("eu-west-1");
        TranscribeClient transcribeClient = Transcribe2ClientFactory.getTranscribeClient(transcribe2Configuration);
        assertNotNull(transcribeClient);
    }

    @Test
    public void getSessionTokenTranscribeClientIsNotNull() {
        Transcribe2Configuration transcribe2Configuration = new Transcribe2Configuration();
        transcribe2Configuration.setAccessKey("accesskey");
        transcribe2Configuration.setSecretKey("secretkey");
        transcribe2Configuration.setSessionToken("sessionToken");
        transcribe2Configuration.setUseSessionCredentials(true);
        transcribe2Configuration.setRegion("eu-west-1");
        TranscribeClient transcribeClient = Transcribe2ClientFactory.getTranscribeClient(transcribe2Configuration);
        assertNotNull(transcribeClient);
    }

    @Test
    public void getStandardTranscribeClientWithProxyIsNotNull() {
        Transcribe2Configuration transcribe2Configuration = new Transcribe2Configuration();
        transcribe2Configuration.setAccessKey("accesskey");
        transcribe2Configuration.setSecretKey("secretkey");
        transcribe2Configuration.setRegion("eu-west-1");
        transcribe2Configuration.setProxyHost("localhost");
        transcribe2Configuration.setProxyPort(9000);
        transcribe2Configuration.setProxyProtocol("HTTP");
        TranscribeClient transcribeClient = Transcribe2ClientFactory.getTranscribeClient(transcribe2Configuration);
        assertNotNull(transcribeClient);
    }

    @Test
    public void getStandardTranscribeClientWithProxyAndAuthIsNotNull() {
        Transcribe2Configuration transcribe2Configuration = new Transcribe2Configuration();
        transcribe2Configuration.setAccessKey("accesskey");
        transcribe2Configuration.setSecretKey("secretkey");
        transcribe2Configuration.setRegion("eu-west-1");
        transcribe2Configuration.setProxyHost("localhost");
        transcribe2Configuration.setProxyPort(9000);
        transcribe2Configuration.setProxyProtocol("HTTP");
        transcribe2Configuration.setProxyUsername("proxyuser");
        transcribe2Configuration.setProxyPassword("proxypass");
        TranscribeClient transcribeClient = Transcribe2ClientFactory.getTranscribeClient(transcribe2Configuration);
        assertNotNull(transcribeClient);
    }
}
