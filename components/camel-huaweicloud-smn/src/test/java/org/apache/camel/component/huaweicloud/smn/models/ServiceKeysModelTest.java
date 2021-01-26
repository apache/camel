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
package org.apache.camel.component.huaweicloud.smn.models;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

public class ServiceKeysModelTest {
    String[] expectedFields = {
            "authenticationKey",
            "secretKey" };

    @Test
    public void testServiceKeysFields() {
        Field[] declaredFields = ServiceKeys.class.getDeclaredFields();

        Assert.assertEquals(2, declaredFields.length);

        List<String> fieldNameList = Arrays.asList(declaredFields).stream()
                .map(field -> field.getName())
                .collect(Collectors.toList());

        for (String expectedField : expectedFields) {
            Assert.assertTrue(fieldNameList.contains(expectedField));
        }
    }
}
