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
package org.apache.camel.java.out;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TryDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TryModelWriterTest {
    static Stream<Arguments> clauses() {
        return Stream.of(false, true).flatMap(properties -> Stream.of(
                Arguments.of(properties, 2, true), Arguments.of(properties, 2, false),
                Arguments.of(properties, 0, true)));
    }

    @ParameterizedTest
    @MethodSource("clauses")
    void clausesSurviveDump(boolean properties, int catchCount, boolean hasFinally) throws Exception {
        TryDefinition def = new TryDefinition();
        def.addOutput(new ToDefinition("mock:try"));
        List<CatchDefinition> catches = new ArrayList<>();
        for (int i = 0; i < catchCount; i++) {
            CatchDefinition clause = new CatchDefinition();
            clause.getExceptions().add(i == 0 ? "java.lang.IllegalStateException" : "java.lang.Exception");
            clause.addOutput(new ToDefinition("mock:catch" + i));
            catches.add(clause);
            if (!properties) {
                def.addOutput(clause);
            }
        }
        if (properties) {
            def.setCatchClauses(catches);
        }
        if (hasFinally) {
            FinallyDefinition clause = new FinallyDefinition();
            clause.addOutput(new ToDefinition("mock:finally"));
            if (properties) {
                def.setFinallyClause(clause);
            } else {
                def.addOutput(clause);
            }
        }
        List<?> originalOutputs = new ArrayList<>(def.getOutputs());
        String output = dump(def);
        Assertions.assertTrue(output.contains("mock:try"), output);
        int previous = output.indexOf("mock:try");
        for (int i = 0; i < catchCount; i++) {
            String uri = "mock:catch" + i;
            int position = output.indexOf(uri);
            Assertions.assertTrue(position > previous, output);
            Assertions.assertEquals(position, output.lastIndexOf(uri), output);
            previous = position;
        }
        if (hasFinally) {
            int position = output.indexOf("mock:finally");
            Assertions.assertTrue(position > previous, output);
            Assertions.assertEquals(position, output.lastIndexOf("mock:finally"), output);
        }
        Assertions.assertEquals(originalOutputs, def.getOutputs());
    }

    @Test
    void dumpingAfterAddingOutputPreservesNewStepAndMixedClauses() throws Exception {
        TryDefinition def = new TryDefinition();
        def.addOutput(new ToDefinition("mock:try"));
        CatchDefinition clause = new CatchDefinition();
        clause.getExceptions().add("java.lang.Exception");
        clause.addOutput(new ToDefinition("mock:catch"));
        def.setCatchClauses(List.of(clause));
        FinallyDefinition finallyClause = new FinallyDefinition();
        finallyClause.addOutput(new ToDefinition("mock:finally"));
        def.addOutput(finallyClause);
        String first = dump(def);
        Assertions.assertTrue(first.indexOf("mock:catch") > first.indexOf("mock:try"), first);
        Assertions.assertTrue(first.indexOf("mock:finally") > first.indexOf("mock:catch"), first);
        def.addOutput(new ToDefinition("mock:later"));
        String second = dump(def);
        Assertions.assertTrue(second.contains("mock:later"), second);
        Assertions.assertEquals(second.indexOf("mock:catch"), second.lastIndexOf("mock:catch"), second);
        Assertions.assertEquals(second.indexOf("mock:finally"), second.lastIndexOf("mock:finally"), second);
        Assertions.assertEquals(3, def.getOutputs().size());
    }

    private String dump(TryDefinition def) throws Exception {
        return new JavaDslModelWriter().writeTryDefinition(def);
    }
}
