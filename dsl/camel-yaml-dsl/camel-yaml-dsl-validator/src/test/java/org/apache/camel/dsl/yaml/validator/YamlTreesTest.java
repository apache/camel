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
package org.apache.camel.dsl.yaml.validator;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTreesTest {

    @Test
    void scalarsReadAsJackson2Did() {
        JsonNode tree
                = YamlTrees.newMapper().readTree("a: ~\nb: Null\nc: 0x1F\nd: 0123\ne: +12\nf: true\ng: 1.5\nh:\ni: 08540\n");

        assertThat(tree.get("a").isNull()).isTrue();
        assertThat(tree.get("b").isNull()).isTrue();
        assertThat(tree.get("c").asInt()).isEqualTo(31);
        assertThat(tree.get("d").asInt()).isEqualTo(123);
        assertThat(tree.get("e").asInt()).isEqualTo(12);
        assertThat(tree.get("f").isBoolean()).isTrue();
        assertThat(tree.get("g").isNumber()).isTrue();
        assertThat(tree.get("h").isNull()).isTrue();
        assertThat(tree.get("i").asInt()).isEqualTo(8540);
    }
}
