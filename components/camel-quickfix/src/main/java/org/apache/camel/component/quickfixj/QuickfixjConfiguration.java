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
package org.apache.camel.component.quickfixj;

import java.util.HashMap;
import java.util.Map;

import quickfix.ConfigError;
import quickfix.Dictionary;
import quickfix.SessionID;
import quickfix.SessionSettings;

public class QuickfixjConfiguration {

    private Map<Object, Object> defaultSettings;
    private Map<SessionID, Map<Object, Object>> sessionSettings;

    public QuickfixjConfiguration() {
    }

    public Map<Object, Object> getDefaultSettings() {
        return defaultSettings;
    }

    public void setDefaultSettings(Map<Object, Object> defaultSettings) {
        this.defaultSettings = defaultSettings;
    }

    public Map<SessionID, Map<Object, Object>> getSessionSettings() {
        return sessionSettings;
    }

    public void setSessionSettings(Map<SessionID, Map<Object, Object>> sessionSettings) {
        this.sessionSettings = sessionSettings;
    }

    public void addSessionSetting(SessionID sessionID, Map<Object, Object> settings) {
        if (sessionSettings == null) {
            sessionSettings = new HashMap<>();
        }
        sessionSettings.put(sessionID, settings);
    }

    public SessionSettings createSessionSettings() throws ConfigError {
        SessionSettings settings = new SessionSettings();
        if (defaultSettings != null && !defaultSettings.isEmpty()) {
            settings.set(new Dictionary("defaults", defaultSettings));
        }

        if (sessionSettings != null && !sessionSettings.isEmpty()) {
            for (Map.Entry<SessionID, Map<Object, Object>> sessionSetting : sessionSettings.entrySet()) {
                settings.set(sessionSetting.getKey(), new Dictionary("session", sessionSetting.getValue()));
            }
        }
        return settings;
    }
}
