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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25437: --prop of camel cmd send and receive takes its value after a space too, instead of losing it to the name
 * of the integration.
 */
class CamelActionPropOptionTest {

    @Test
    void sendTakesAPropertyAfterASpace() {
        CamelSendAction send = new CamelSendAction(new CamelJBangMain());
        new CommandLine(send).parseArgs("--prop", "greeting=Hi", "--prop=other=1");

        assertThat(send.property).containsExactly("greeting=Hi", "other=1");
        assertThat(send.name).isNull();
    }

    @Test
    void receiveTakesAPropertyAfterASpace() {
        CamelReceiveAction receive = new CamelReceiveAction(new CamelJBangMain());
        new CommandLine(receive).parseArgs("--prop", "greeting=Hi");

        assertThat(receive.property).containsExactly("greeting=Hi");
    }
}
