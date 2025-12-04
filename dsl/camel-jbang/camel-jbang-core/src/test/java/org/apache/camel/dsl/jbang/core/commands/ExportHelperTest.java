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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExportHelperTest {

    @Test
    public void testPackageName() {
        String name = ExportHelper.exportPackageName("org.demo", "some-app-x2025", null);
        Assertions.assertEquals("org.demo.someappx2025", name);
        name = ExportHelper.exportPackageName("org.demo", "some-app-2025", null);
        Assertions.assertEquals("org.demo.someapp2025", name);
    }
}
