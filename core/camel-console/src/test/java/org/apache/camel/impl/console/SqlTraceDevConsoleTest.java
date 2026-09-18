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
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SqlTraceDevConsole only records statements sent to a real sql:/jdbc: endpoint, and camel-console has no dependency on
 * camel-sql/camel-jdbc - so the ring buffer is always empty here. This test only verifies the console's basic shape.
 */
public class SqlTraceDevConsoleTest extends ContextTestSupport {

    @Test
    public void testSqlTraceConsoleText() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-trace");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("sql-trace", con.getId());

        // events[] is only allocated in doInit(), normally triggered via DevConsoleRegistry.register()
        ServiceHelper.startService(con);

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        Assertions.assertEquals("", out);
    }

    @Test
    public void testSqlTraceConsoleJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-trace");
        Assertions.assertNotNull(con);
        ServiceHelper.startService(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);
        Assertions.assertFalse(out.containsKey("statements"));
        Assertions.assertFalse(out.containsKey("summary"));
    }
}
