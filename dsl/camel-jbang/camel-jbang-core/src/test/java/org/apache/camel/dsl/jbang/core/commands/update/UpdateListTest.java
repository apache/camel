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
package org.apache.camel.dsl.jbang.core.commands.update;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class UpdateListTest extends CamelCommandBaseTest {

    @Test
    public void listUpdateVersions() throws Exception {
        UpdateList infraList = new UpdateList(new CamelJBangMain().withPrinter(printer));

        infraList.doCall();

        List<String> lines = printer.getLines();
        Assertions.assertThat(lines.stream().collect(Collectors.joining("\n")))
                .contains("Migrates Apache Camel 4 application to Apache Camel 4.9.0");
    }
}
