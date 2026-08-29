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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HeapDumpDevConsoleTest extends ContextTestSupport {

    @Test
    public void testHeapDumpConsole() throws Exception {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("heap-dump");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("jvm", con.getGroup());
        Assertions.assertEquals("heap-dump", con.getId());

        Map<String, Object> options = new HashMap<>();
        options.put(HeapDumpDevConsole.NAME, "camel-console-test-heap-dump");

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);

        String error = out.getString("error");
        if (error == null) {
            String file = out.getString("file");
            Assertions.assertNotNull(file);
            Assertions.assertNotNull(out.getLong("size"));
            new File(file).delete();
        }
    }
}
