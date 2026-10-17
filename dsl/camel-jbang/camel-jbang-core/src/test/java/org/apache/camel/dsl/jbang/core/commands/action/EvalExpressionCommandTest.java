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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class EvalExpressionCommandTest extends ActionCommandTestSupport {

    @Test
    void testTheExpressionAndBodyAreSentAsTheyAre() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        EvalExpressionCommand command = new EvalExpressionCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "orders", "--template=${body} contains \"/\"",
                "--body={\"path\":\"a/b\"}");

        int exit = callWithResponse(command, new JsonObject(Map.of("status", "success", "result", "true")));

        assertEquals(0, exit);
        // the integration does not unescape them: escaped, the quotes and slashes would be part of the expression
        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("eval", action.getString("action"));
        assertEquals("${body} contains \"/\"", action.getString("template"));
        assertEquals("{\"path\":\"a/b\"}", action.getString("body"));
    }
}
