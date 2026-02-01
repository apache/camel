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
package org.apache.camel.impl.console;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleLanguageDevConsoleTest extends ContextTestSupport {

    @Test
    public void testSimpleLanguageTest() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("simple-language");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("simple-language", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        Assertions.assertTrue(out.contains("Custom Functions: 0"));
    }

    @Test
    public void testSimpleLanguageJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("simple-language");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("simple-language", con.getId());

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertEquals(0, out.getInteger("size"));
        Assertions.assertNotNull(out);
    }

}
