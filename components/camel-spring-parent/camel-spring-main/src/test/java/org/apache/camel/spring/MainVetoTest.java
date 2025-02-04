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
package org.apache.camel.spring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainVetoTest {

    @Test
    public void testMain() {
        // lets make a simple route
        assertDoesNotThrow(() -> {
            Main main = new Main();
            main.configure().setDurationMaxSeconds(1);
            main.configure().setDurationHitExitCode(99);
            main.setApplicationContextUri("org/apache/camel/spring/MainVetoTest.xml");

            // should not hang as we veto fail
            main.run();

            // should complete normally due veto
            assertEquals(99, main.getExitCode());

        }

        );

    }

}
