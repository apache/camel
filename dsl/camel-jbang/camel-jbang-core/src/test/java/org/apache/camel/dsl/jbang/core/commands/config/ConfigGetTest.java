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

package org.apache.camel.dsl.jbang.core.commands.config;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.UserConfigHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigGetTest extends CamelCommandBaseTest {

    @Test
    public void shouldGetConfig() throws Exception {
        UserConfigHelper.createUserConfig("foo=bar");

        ConfigGet command = new ConfigGet(new CamelJBangMain().withPrinter(printer));
        command.key = "foo";
        command.doCall();

        Assertions.assertEquals("bar", printer.getOutput());
    }

    @Test
    public void shouldHandleConfigGetKeyNotFound() throws Exception {
        UserConfigHelper.createUserConfig("");

        ConfigGet command = new ConfigGet(new CamelJBangMain().withPrinter(printer));
        command.key = "foo";
        command.doCall();

        Assertions.assertEquals("foo key not found", printer.getOutput());
    }

}
