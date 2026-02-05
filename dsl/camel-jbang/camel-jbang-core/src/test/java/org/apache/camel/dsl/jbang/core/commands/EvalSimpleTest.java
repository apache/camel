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
package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.commands.action.EvalExpressionCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class EvalSimpleTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldEvalSimple() throws Exception {
        String[] args = new String[] { "--template=${length()}", "--body=hello_world" };
        EvalExpressionCommand command = createCommand(args);
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        var lines = printer.getLines();
        Assertions.assertNotNull(lines);
        Assertions.assertEquals(2, lines.size());
        Assertions.assertEquals("11", lines.get(1));
    }

    private EvalExpressionCommand createCommand(String... args) {
        EvalExpressionCommand command = new EvalExpressionCommand(new CamelJBangMain().withPrinter(printer));
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        return command;
    }

}
