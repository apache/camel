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
package org.apache.camel.component.splunk.integration;

import org.apache.camel.component.splunk.event.SplunkEvent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;

public abstract class SplunkTest extends CamelTestSupport {
    // TEST WILL RUN ON SPLUNK DEFAULT LOCALHOST+PORT
    // the Splunk username/pw created when Splunk was initialized during your
    // login.

    protected static final String SPLUNK_USERNAME = "admin";
    protected static final String SPLUNK_PASSWORD = "preben1212";
    // should be created in splunk before test run;
    protected static final String INDEX = "junit";

    @Before
    public void init() throws Exception {
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key1", "value1");
        splunkEvent.addPair("key2", "value2");
        splunkEvent.addPair("key3", "value3");
        template.sendBody("direct:submit", splunkEvent);
    }

}
