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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunTransformMessageTest {

    @Test
    void theEmptyCamelOfATransformLoadsNoPlugins() {
        // camel eval --isolated and camel transform message boot an empty Camel. Loading the plugins there is not
        // needed, and EvalSimpleTest, which uses the home of the developer, then rewrote the plugin class path cache of
        // the developer's Camel CLI with the class path of the test JVM (camel test run failed after that)
        Run run = new Run(new CamelJBangMain());
        run.prepareTransformMessage("4.24.0", null);

        assertThat(run.skipPlugins).isTrue();
        assertThat(run.transformMessageRun).isTrue();
        assertThat(run.empty).isTrue();
        assertThat(run.background).isTrue();
        assertThat(run.camelVersion).isEqualTo("4.24.0");
    }

}
