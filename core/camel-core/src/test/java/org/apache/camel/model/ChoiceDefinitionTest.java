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

import org.apache.camel.TestSupport;
import org.junit.Test;

/**
 *
 */
public class ChoiceDefinitionTest extends TestSupport {

    @Test
    public void testChoiceOutputOrder() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        choice.addOutput(when1);
        choice.addOutput(when2);
        choice.addOutput(other);

        assertEquals(3, choice.getOutputs().size());
        assertEquals(when1, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));
        assertEquals(other, choice.getOutputs().get(2));
        assertEquals("choice[when[{${body} contains Camel}],when[{${body} contains Donkey}],otherwise[]]", choice.getLabel());
    }

    @Test
    public void testChoiceOutputOrderIterate() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        choice.addOutput(when1);
        choice.addOutput(when2);
        choice.addOutput(other);

        assertEquals(3, choice.getOutputs().size());
        int i = 0;
        for (ProcessorDefinition<?> def : choice.getOutputs()) {
            if (i == 0) {
                assertEquals(when1, def);
            } else if (i == 1) {
                assertEquals(when2, def);
            } else {
                assertEquals(other, def);
            }
            i++;
        }
    }

    @Test
    public void testChoiceOutputOrderNoOtherwise() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));

        choice.addOutput(when1);
        choice.addOutput(when2);

        assertEquals(2, choice.getOutputs().size());
        assertEquals(when1, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));
    }

    @Test
    public void testChoiceOutputOrderNoOtherwiseIterate() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));

        choice.addOutput(when1);
        choice.addOutput(when2);

        assertEquals(2, choice.getOutputs().size());
        assertEquals(when1, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));

        assertEquals(2, choice.getOutputs().size());
        int i = 0;
        for (ProcessorDefinition<?> def : choice.getOutputs()) {
            if (i == 0) {
                assertEquals(when1, def);
            } else if (i == 1) {
                assertEquals(when2, def);
            }
            i++;
        }
    }

    @Test
    public void testChoiceOtherwiseAlwaysLast() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        // add otherwise in between
        choice.addOutput(when1);
        choice.addOutput(other);
        choice.addOutput(when2);

        // should ensure otherwise is last
        assertEquals(3, choice.getOutputs().size());
        assertEquals(when1, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));
        assertEquals(other, choice.getOutputs().get(2));
    }

    @Test
    public void testChoiceOtherwiseAlwaysLastIterate() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        // add otherwise in between
        choice.addOutput(when1);
        choice.addOutput(other);
        choice.addOutput(when2);

        // should ensure otherwise is last
        assertEquals(3, choice.getOutputs().size());
        assertEquals(when1, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));
        assertEquals(other, choice.getOutputs().get(2));

        assertEquals(3, choice.getOutputs().size());
        int i = 0;
        for (ProcessorDefinition<?> def : choice.getOutputs()) {
            if (i == 0) {
                assertEquals(when1, def);
            } else if (i == 1) {
                assertEquals(when2, def);
            } else {
                assertEquals(other, def);
            }
            i++;
        }
    }

    @Test
    public void testChoiceOutputRemoveFirst() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        choice.addOutput(when1);
        choice.addOutput(when2);
        choice.addOutput(other);

        assertEquals(3, choice.getOutputs().size());
        choice.getOutputs().remove(0);
        assertEquals(2, choice.getOutputs().size());
        assertEquals(when2, choice.getOutputs().get(0));
        assertEquals(other, choice.getOutputs().get(1));
    }

    @Test
    public void testChoiceOutputRemoveLast() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        choice.addOutput(when1);
        choice.addOutput(when2);
        choice.addOutput(other);

        assertEquals(3, choice.getOutputs().size());
        choice.getOutputs().remove(2);
        assertEquals(2, choice.getOutputs().size());
        assertEquals(when1, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));
    }

    @Test
    public void testChoiceOutputSetFirst() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));
        WhenDefinition when3 = new WhenDefinition(body().contains("Beer"));
        OtherwiseDefinition other = new OtherwiseDefinition();

        choice.addOutput(when1);
        choice.addOutput(when2);
        choice.addOutput(other);

        assertEquals(3, choice.getOutputs().size());
        choice.getOutputs().set(0, when3);
        assertEquals(3, choice.getOutputs().size());
        assertEquals(when3, choice.getOutputs().get(0));
        assertEquals(when2, choice.getOutputs().get(1));
        assertEquals(other, choice.getOutputs().get(2));
    }

    @Test
    public void testChoiceOutputClear() throws Exception {
        ChoiceDefinition choice = new ChoiceDefinition();
        WhenDefinition when1 = new WhenDefinition(body().contains("Camel"));
        WhenDefinition when2 = new WhenDefinition(body().contains("Donkey"));

        choice.addOutput(when1);
        choice.addOutput(when2);

        assertEquals(2, choice.getOutputs().size());
        choice.getOutputs().clear();
        assertEquals(0, choice.getOutputs().size());
    }

}
