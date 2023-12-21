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
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigUnsetTest extends CamelCommandBaseTest {

    @Test
    public void shouldUnsetConfig() throws Exception {
        UserConfigHelper.createUserConfig("""
                camel-version=latest
                foo=bar
                kamelets-version=greatest
                """);

        ConfigUnset command = new ConfigUnset(new CamelJBangMain().withPrinter(printer));
        command.key = "foo";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        CommandLineHelper.loadProperties(properties -> {
            Assertions.assertEquals(2, properties.size());
            Assertions.assertEquals("latest", properties.get("camel-version"));
            Assertions.assertEquals("greatest", properties.get("kamelets-version"));
        });
    }

    @Test
    public void shouldHandleMissingKeyToUnset() throws Exception {
        UserConfigHelper.createUserConfig("""
                camel-version=latest
                kamelets-version=greatest
                """);

        ConfigUnset command = new ConfigUnset(new CamelJBangMain().withPrinter(printer));
        command.key = "foo";
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());

        CommandLineHelper.loadProperties(properties -> {
            Assertions.assertEquals(2, properties.size());
            Assertions.assertEquals("latest", properties.get("camel-version"));
            Assertions.assertEquals("greatest", properties.get("kamelets-version"));
        });
    }

}
