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
package org.apache.camel.main.download;

import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.component.timer.TimerComponent;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class KameletMainInjectorTest {

    @Test
    void testWildcardStubsNonWhitelistedComponent() {
        // TimerComponent is NOT in ACCEPTED_STUB_NAMES, stubPattern="*" (used by camel export), it should be replaced with StubComponent.
        KameletMainInjector injector = new KameletMainInjector(
                new SimpleCamelContext().getInjector(), "*", true);

        Object result = injector.newInstance(TimerComponent.class);
        assertInstanceOf(StubComponent.class, result);
    }

    @Test
    void testComponentWildcardStubsNonWhitelistedComponent() {
        // stubPattern="component:*" (used by --stub=all) should also stub non-whitelisted components.
        KameletMainInjector injector = new KameletMainInjector(
                new SimpleCamelContext().getInjector(), "component:*", true);

        Object result = injector.newInstance(TimerComponent.class);
        assertInstanceOf(StubComponent.class, result);
    }

    @Test
    void testWildcardAllowsWhitelistedComponent() {
        // DirectComponent is in ACCEPTED_STUB_NAMES, so it should not be stubbed.
        KameletMainInjector injector = new KameletMainInjector(
                new SimpleCamelContext().getInjector(), "*", true);

        Object result = injector.newInstance(DirectComponent.class);
        assertInstanceOf(DirectComponent.class, result);
    }

    @Test
    void testNonComponentClassIsNotStubbed() {
        // Non-Component classes should pass through unchanged.
        KameletMainInjector injector = new KameletMainInjector(
                new SimpleCamelContext().getInjector(), "*", true);

        Object result = injector.newInstance(String.class);
        assertInstanceOf(String.class, result);
    }
}
