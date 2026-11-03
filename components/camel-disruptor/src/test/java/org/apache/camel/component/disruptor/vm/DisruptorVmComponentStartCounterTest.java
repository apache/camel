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
package org.apache.camel.component.disruptor.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only a started disruptor-vm component counts as started: one that was only initialized does not change the count when
 * it is stopped.
 */
class DisruptorVmComponentStartCounterTest {

    @Test
    void componentThatWasOnlyInitializedDoesNotChangeTheCount() {
        int before = DisruptorVmComponent.getStartCounter();
        CamelContext context = new DefaultCamelContext();
        try {
            DisruptorVmComponent initialized = new DisruptorVmComponent();
            initialized.setCamelContext(context);
            initialized.init();
            initialized.stop();
            assertEquals(before, DisruptorVmComponent.getStartCounter());

            DisruptorVmComponent started = new DisruptorVmComponent();
            started.setCamelContext(context);
            started.start();
            assertEquals(before + 1, DisruptorVmComponent.getStartCounter());
            started.stop();
            started.stop();
            assertEquals(before, DisruptorVmComponent.getStartCounter());
        } finally {
            context.stop();
        }
    }
}
