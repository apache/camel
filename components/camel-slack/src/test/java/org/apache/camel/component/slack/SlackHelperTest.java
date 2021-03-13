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

import com.slack.api.SlackConfig;
import org.apache.camel.component.slack.helper.SlackHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SlackHelperTest {

    @Test
    public void testCreateDefaultSlackConfigForNullServerUrl() {
        SlackConfig slackConfig = SlackHelper.createSlackConfig(null);
        assertEquals(SlackConfig.DEFAULT, slackConfig);
    }

    @Test
    public void testCreateDefaultSlackConfigForEmptyServerUrl() {
        SlackConfig slackConfig = SlackHelper.createSlackConfig("");
        assertEquals(SlackConfig.DEFAULT, slackConfig);
    }

    @Test
    public void testCreateSlackConfigForServerUrl() {
        String serverUrl = "http://foo.bar.com";
        SlackConfig slackConfig = SlackHelper.createSlackConfig(serverUrl);
        assertEquals(serverUrl + "/api/", slackConfig.getMethodsEndpointUrlPrefix());
    }
}
