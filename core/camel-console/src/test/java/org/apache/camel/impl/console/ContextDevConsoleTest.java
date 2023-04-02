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

public class ContextDevConsoleTest extends ContextTestSupport {

    @Test
    public void testContextTest() throws Exception {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("context");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("context", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        log.info(out);
        Assertions.assertTrue(out.contains(context.getName()));
    }

    @Test
    public void testContextJson() throws Exception {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("context");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("context", con.getId());

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);
        Assertions.assertEquals(context.getName(), out.getString("name"));
    }

}
