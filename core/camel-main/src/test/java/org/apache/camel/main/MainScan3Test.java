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
package org.apache.camel.main;

import org.apache.camel.FailedToCreateRouteException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MainScan3Test {

    @Test
    public void testScan3() {
        Main main = new Main();
        main.configure().withBasePackageScan("org.apache.camel.main.scan3");
        try {
            main.start();
            fail();
        } catch (FailedToCreateRouteException e) {
            assertEquals(
                    "Failed to create route because: Duplicate route ids detected: foo2. Please correct ids to be unique among all your routes.",
                    e.getMessage());
        }

        main.stop();
    }

}
