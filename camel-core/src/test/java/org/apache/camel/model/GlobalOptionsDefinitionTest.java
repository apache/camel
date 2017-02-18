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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GlobalOptionsDefinitionTest {

    private static final String LOG_DEBUG_BODY_MAX_CHARS_VALUE = "500";
    private static final String LOG_DEBUG_BODY_MAX_CHARS_DUP_VALUE = "400";

    private GlobalOptionsDefinition instance;
    private List<GlobalOptionDefinition> globalOptions;
    private GlobalOptionDefinition nominalOption;
    private GlobalOptionDefinition duplicateOption;

    @Before
    public void setup() {
        nominalOption = new GlobalOptionDefinition();
        nominalOption.setKey(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
        nominalOption.setValue(LOG_DEBUG_BODY_MAX_CHARS_VALUE);

        duplicateOption = new GlobalOptionDefinition();
        duplicateOption.setKey(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
        duplicateOption.setValue(LOG_DEBUG_BODY_MAX_CHARS_DUP_VALUE);

        globalOptions = new ArrayList<>();
        globalOptions.add(nominalOption);
        instance = new GlobalOptionsDefinition();
        instance.setGlobalOptions(globalOptions);
    }

    @Test
    public void asMapShouldCarryOnLogDebugMaxChars() {
        Map<String, String> map = instance.asMap();
        Assert.assertNotNull(map);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(LOG_DEBUG_BODY_MAX_CHARS_VALUE, map.get(Exchange.LOG_DEBUG_BODY_MAX_CHARS));
    }

    @Test
    public void asMapWithDuplicateKeyShouldOverride() {
        globalOptions.add(duplicateOption);
        Map<String, String> map = instance.asMap();
        Assert.assertNotNull(map);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(LOG_DEBUG_BODY_MAX_CHARS_DUP_VALUE, map.get(Exchange.LOG_DEBUG_BODY_MAX_CHARS));
    }

    @Test(expected = NullPointerException.class)
    public void asMapWithNullGlobalOptionsShouldThrowNullPointerException() {
        instance.setGlobalOptions(null);
        instance.asMap();
    }

    @Test
    public void asMapWithEmptyGlobalOptionsShouldReturnEmptyMap() {
        globalOptions.clear();
        Map<String, String> map = instance.asMap();
        Assert.assertNotNull(map);
        Assert.assertEquals(0, map.size());
    }

    @Test
    public void asMapWithNullKeyShouldReturnEmptyMap() {
        nominalOption.setKey(null);
        Map<String, String> map = instance.asMap();
        Assert.assertNotNull(map);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(LOG_DEBUG_BODY_MAX_CHARS_VALUE, map.get(null));
    }

}
