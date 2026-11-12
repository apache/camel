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
package org.apache.camel.component.zendesk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZendeskConfigurationTest {

    @Test
    public void toStringShouldNotLeakCredentials() {
        ZendeskConfiguration configuration = new ZendeskConfiguration();
        configuration.setServerUrl("https://example.zendesk.com");
        configuration.setUsername("someone@example.com");
        configuration.setPassword("s3cret");
        configuration.setToken("t0ken");
        configuration.setOauthToken("0authT0ken");

        String actual = configuration.toString();

        assertFalse(actual.contains("s3cret"), actual);
        assertFalse(actual.contains("t0ken"), actual);
        assertFalse(actual.contains("0authT0ken"), actual);
        assertTrue(actual.contains("password=xxxxxx"), actual);
        assertTrue(actual.contains("token=xxxxxx"), actual);
        assertTrue(actual.contains("oauthToken=xxxxxx"), actual);
        // what the configuration points at stays readable, so the line is still useful
        assertTrue(actual.contains("serverUrl=https://example.zendesk.com"), actual);
    }

    @Test
    public void toStringShouldStillShowThatACredentialIsUnset() {
        ZendeskConfiguration configuration = new ZendeskConfiguration();

        String actual = configuration.toString();

        assertTrue(actual.contains("password=null"), actual);
        assertTrue(actual.contains("token=null"), actual);
    }

}
