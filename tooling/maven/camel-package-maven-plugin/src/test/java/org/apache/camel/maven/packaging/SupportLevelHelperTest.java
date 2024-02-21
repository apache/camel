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
package org.apache.camel.maven.packaging;

import org.apache.camel.tooling.model.SupportLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SupportLevelHelperTest {

    @Test
    public void testPreview() {
        Assertions.assertEquals(SupportLevel.Preview, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.20.0"));
        Assertions.assertEquals(SupportLevel.Preview, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.20.1"));
        Assertions.assertEquals(SupportLevel.Preview, SupportLevelHelper.defaultSupportLevel("3.19.1", "3.20.1"));
        Assertions.assertEquals(SupportLevel.Preview, SupportLevelHelper.defaultSupportLevel("3.19.1", "3.20.2"));
        Assertions.assertNotEquals(SupportLevel.Preview, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.21.0"));
    }

    @Test
    public void testStable() {
        Assertions.assertNotEquals(SupportLevel.Stable, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.20.0"));
        Assertions.assertEquals(SupportLevel.Stable, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.21.0"));
        Assertions.assertEquals(SupportLevel.Stable, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.21.1"));
        Assertions.assertEquals(SupportLevel.Stable, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.22.0"));
        Assertions.assertEquals(SupportLevel.Stable, SupportLevelHelper.defaultSupportLevel("3.19.0", "3.22.3"));
    }

}
