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
package org.apache.camel.component.splunk;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.component.splunk.event.SplunkEvent;
import org.junit.Assert;
import org.junit.Test;

public class SplunkEventTest extends Assert {

    @Test
    public void testEventDataWithQuotedValues() {
        Date now = new Date();
        SplunkEvent event = new SplunkEvent("testevent", "123", false, true);
        event.addPair("key1", "value1");
        event.addPair("key2", "value2 with whitespace");
        event.addPair(SplunkEvent.COMMON_DVC_TIME, now);
        assertEquals("Values should be quoted", "name=\"testevent\" event_id=\"123\" key1=\"value1\" key2=\"value2 with whitespace\" dvc_time=\"" + now.toString() + "\"\n",
                     event.toString());
        assertEquals(5, event.getEventData().size());
        assertTrue(event.getEventData().get("key2").equals("value2 with whitespace"));
    }

    @Test
    public void testEventDataFromMap() {
        String rawString = "2013-10-26    15:16:38:011+0200 name=\"twitter-message\" from_user=\"MyNameIsZack_98\" in_reply_to=\"null\" start_time=\"Sat Oct 26 15:16:21 CEST 2013\" "
                           + "event_id=\"394090123278974976\" text=\"RT @RGIII: Just something about music that it can vibe with your soul\" retweet_count=\"1393\"";
        Map<String, String> eventData = new LinkedHashMap<>();
        eventData.put("_subsecond", ".011");
        eventData.put("_raw", rawString);
        SplunkEvent splunkEvent = new SplunkEvent(eventData);
        assertTrue(splunkEvent.toString().contains("_subsecond=\".011\" _raw=\"" + rawString + "\"\n"));
    }
}
