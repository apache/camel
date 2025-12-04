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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.junit.jupiter.api.Test;

public class JolokiaTraitTest {

    @Test
    public void jolokiaExposeFalseTest() {
        TraitContext context = mock(TraitContext.class);

        Traits traitConfig = mock(Traits.class);
        Jolokia jolokia = new Jolokia();
        jolokia.setExpose(false);
        when(traitConfig.getJolokia()).thenReturn(jolokia);

        JolokiaTrait trait = new JolokiaTrait();
        trait.apply(traitConfig, context);

        verify(context, times(1)).doWithDeployments(any());
        verify(context, times(1)).doWithKnativeServices(any());
        verify(context, times(0)).doWithServices(any());
    }

    @Test
    public void jolokiaExposeTrueTest() {
        TraitContext context = mock(TraitContext.class);

        Traits traitConfig = mock(Traits.class);
        Jolokia jolokia = new Jolokia();
        jolokia.setExpose(true);
        when(traitConfig.getJolokia()).thenReturn(jolokia);

        JolokiaTrait trait = new JolokiaTrait();
        trait.apply(traitConfig, context);

        verify(context, times(1)).doWithDeployments(any());
        verify(context, times(1)).doWithKnativeServices(any());
        verify(context, times(1)).doWithServices(any());
    }
}
