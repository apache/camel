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
package org.apache.camel.component.slack.helper;

import com.slack.api.SlackConfig;
import org.apache.camel.util.ObjectHelper;

/**
 * Utility methods for Slack
 */
public final class SlackHelper {

    private SlackHelper() {
        // Utility class
    }

    /**
     * Creates a {@link SlackConfig} instance.
     *
     * @param  serverUrl The Slack server URL to use as prefix for API requests. If null or empty then the default
     *                   SlackConfig instance is returned.
     * @return           A new {@link SlackConfig} instance.
     */
    public static SlackConfig createSlackConfig(String serverUrl) {
        SlackConfig config;
        if (ObjectHelper.isNotEmpty(serverUrl)) {
            config = new SlackConfig();
            config.setMethodsEndpointUrlPrefix(serverUrl + "/api/");
        } else {
            config = SlackConfig.DEFAULT;
        }
        return config;
    }
}
