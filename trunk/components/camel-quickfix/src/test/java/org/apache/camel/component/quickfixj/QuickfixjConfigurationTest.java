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
package org.apache.camel.component.quickfixj;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import quickfix.SessionID;
import quickfix.SessionSettings;

public class QuickfixjConfigurationTest {

    @Test
    public void testConfiguration() throws Exception {
        QuickfixjConfiguration factory = new QuickfixjConfiguration();

        Map<Object, Object> defaultSettings = new HashMap<Object, Object>();
        defaultSettings.put("value1", 1);
        defaultSettings.put("value2", 2);

        factory.setDefaultSettings(defaultSettings);

        Map<Object, Object> session1Settings = new HashMap<Object, Object>();
        session1Settings.put("value1", 10);
        session1Settings.put("value3", 30);

        Map<SessionID, Map<Object, Object>> sessionSettings = new HashMap<SessionID, Map<Object, Object>>();

        SessionID sessionID = new SessionID("FIX.4.2:SENDER->TARGET");
        sessionSettings.put(sessionID, session1Settings);

        factory.setSessionSettings(sessionSettings);

        SessionSettings settings = factory.createSessionSettings();
        Properties sessionProperties = settings.getSessionProperties(sessionID, true);

        Assert.assertThat(sessionProperties.get("value1").toString(), CoreMatchers.is("10"));
        Assert.assertThat(sessionProperties.get("value2").toString(), CoreMatchers.is("2"));
        Assert.assertThat(sessionProperties.get("value3").toString(), CoreMatchers.is("30"));
    }
}
