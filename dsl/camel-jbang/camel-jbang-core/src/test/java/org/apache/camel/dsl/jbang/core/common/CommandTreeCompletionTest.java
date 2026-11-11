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
package org.apache.camel.dsl.jbang.core.common;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The completion of the commands, as camel shell and the completion scripts ask aesh for it.
 */
class CommandTreeCompletionTest extends CamelCommandBaseTestSupport {

    private CommandTree.Completion complete(String line) throws Exception {
        return new CamelJBangMain().newCommandLine().complete(line, line.length());
    }

    @Test
    void completesASubCommand() throws Exception {
        CommandTree.Completion completion = complete("camel get rou");
        assertThat(completion.candidates()).contains("route", "route-controller");
        assertThat(completion.offset()).isEqualTo("camel get ".length());
    }

    @Test
    void completesAnOption() throws Exception {
        assertThat(complete("camel get route --so").candidates()).containsExactlyInAnyOrder("--sort", "--source");
    }

    @Test
    void completesTheValueOfAnOption() throws Exception {
        assertThat(complete("camel run --runtime=s").candidates()).contains("spring-boot");
    }
}
