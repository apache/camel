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
package org.apache.camel.scr;

import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class ScrHelperTest {

    @Rule
    public TestName testName = new TestName();

    private Logger log = LoggerFactory.getLogger(getClass());

    @Before
    public void setUp() throws Exception {
        log.info("*******************************************************************");
        log.info("Test: " + testName.getMethodName());
        log.info("*******************************************************************");
    }

    @Test
    public void scrHelperTest() throws Exception {
        Map<String, String> properties = ScrHelper.getScrProperties("src/test/resources/componentDefinitionExample.xml", "my.example.Component");
        assertEquals("exampleContext", properties.get("camelContextId"));
        assertEquals("true", properties.get("active"));
        assertTrue(properties.size() == 6);
    }
}
