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
package org.apache.camel.component.gora.utils;

import org.apache.camel.component.gora.GoraAttribute;
import org.apache.camel.component.gora.GoraConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GORA Utils Tests
 */
public class GoraUtilsTest {

    @Test
    public void configurationExistShouldSucceedtIfMethodExist() throws Exception {
        final GoraConfiguration conf = new GoraConfiguration();
        assertTrue(GoraUtils.configurationExist(GoraAttribute.GORA_QUERY_LIMIT, conf));
    }

    @Test
    public void configurationExistShouldThrowExceptionIfMethodDoesNotExist() {
        final GoraConfiguration conf = new GoraConfiguration();
        assertThrows(NoSuchMethodException.class,
                () -> GoraUtils.configurationExist(GoraAttribute.GORA_KEY, conf));
    }

    @Test
    public void getAttributeAsLongShouldSReturnTheCorrectValue() throws Exception {
        final GoraConfiguration conf = new GoraConfiguration();
        conf.setLimit(3L);
        assertEquals(Long.valueOf(3), GoraUtils.getAttributeAsLong(GoraAttribute.GORA_QUERY_LIMIT, conf));
    }
}
