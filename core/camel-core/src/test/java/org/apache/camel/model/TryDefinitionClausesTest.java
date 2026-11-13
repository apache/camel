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
package org.apache.camel.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The doCatch and doFinally clauses of a doTry are kept in its outputs, also when they are set as properties (as the
 * YAML DSL does), and the clause getters are derived from the outputs (CAMEL-25410).
 */
public class TryDefinitionClausesTest {

    @Test
    public void testClausesSetAsPropertiesAreInTheOutputs() {
        TryDefinition doTry = new TryDefinition();
        CatchDefinition doCatch = newCatch("mock:catch");
        FinallyDefinition doFinally = newFinally("mock:finally");
        doTry.setFinallyClause(doFinally);
        doTry.setCatchClauses(List.of(doCatch));
        // steps written after the clauses (as YAML with doCatch above steps) go before them
        doTry.addOutput(new ToDefinition("mock:a"));
        doTry.addOutput(new ToDefinition("mock:b"));

        assertThat(doTry.getOutputs()).hasSize(4);
        assertThat(((ToDefinition) doTry.getOutputs().get(0)).getUri()).isEqualTo("mock:a");
        assertThat(((ToDefinition) doTry.getOutputs().get(1)).getUri()).isEqualTo("mock:b");
        assertThat(doTry.getOutputs().get(2)).isSameAs(doCatch);
        assertThat(doTry.getOutputs().get(3)).isSameAs(doFinally);

        assertThat(doTry.getCatchClauses()).containsExactly(doCatch);
        assertThat(doTry.getFinallyClause()).isSameAs(doFinally);
        assertThat(doTry.getOutputsWithoutCatches()).hasSize(2);
        assertThat(doCatch.getParent()).isSameAs(doTry);
        assertThat(doFinally.getParent()).isSameAs(doTry);
    }

    @Test
    public void testSettingTheClausesAgainDoesNotDuplicateThem() {
        TryDefinition doTry = new TryDefinition();
        doTry.addOutput(new ToDefinition("mock:a"));
        CatchDefinition doCatch = newCatch("mock:catch");
        doTry.setCatchClauses(List.of(doCatch));
        doTry.setCatchClauses(List.of(doCatch));
        FinallyDefinition first = newFinally("mock:first");
        FinallyDefinition second = newFinally("mock:second");
        doTry.setFinallyClause(first);
        // a doTry has one doFinally: setting another replaces it
        doTry.setFinallyClause(second);

        assertThat(doTry.getOutputs()).hasSize(3);
        assertThat(doTry.getCatchClauses()).containsExactly(doCatch);
        assertThat(doTry.getFinallyClause()).isSameAs(second);
        assertThat(doTry.getOutputs()).doesNotContain(first);
    }

    @Test
    public void testClauseRemovedFromTheOutputsIsGone() {
        TryDefinition doTry = new TryDefinition();
        doTry.addOutput(new ToDefinition("mock:a"));
        CatchDefinition doCatch = newCatch("mock:catch");
        CatchDefinition other = newCatch("mock:other");
        FinallyDefinition doFinally = newFinally("mock:finally");
        doTry.setCatchClauses(List.of(doCatch, other));
        doTry.setFinallyClause(doFinally);
        assertThat(doTry.getCatchClauses()).containsExactly(doCatch, other);

        // as advice-with does: the outputs are changed directly
        doTry.getOutputs().remove(other);
        doTry.getOutputs().remove(doFinally);

        assertThat(doTry.getCatchClauses()).containsExactly(doCatch);
        assertThat(doTry.getFinallyClause()).isNull();
    }

    @Test
    public void testCopyHasOneInstanceOfEachClause() {
        TryDefinition doTry = new TryDefinition();
        doTry.addOutput(new ToDefinition("mock:a"));
        doTry.setCatchClauses(List.of(newCatch("mock:catch")));
        doTry.setFinallyClause(newFinally("mock:finally"));

        TryDefinition copy = doTry.copyDefinition();

        assertThat(copy.getOutputs()).hasSize(3);
        assertThat(copy.getCatchClauses()).hasSize(1);
        assertThat(copy.getOutputs()).contains(copy.getCatchClauses().get(0), copy.getFinallyClause());
        assertThat(copy.getCatchClauses().get(0)).isNotSameAs(doTry.getCatchClauses().get(0));
        assertThat(copy.getCatchClauses().get(0).getParent()).isSameAs(copy);
    }

    private static CatchDefinition newCatch(String uri) {
        CatchDefinition answer = new CatchDefinition();
        answer.getExceptions().add("java.lang.Exception");
        answer.addOutput(new ToDefinition(uri));
        return answer;
    }

    private static FinallyDefinition newFinally(String uri) {
        FinallyDefinition answer = new FinallyDefinition();
        answer.addOutput(new ToDefinition(uri));
        return answer;
    }
}
