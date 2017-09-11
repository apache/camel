/**
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
package org.apache.camel.spring.boot.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        HierarchicalPropertiesEvaluatorTest.TestConfiguration.class
    },
    properties = {
        "test.group1.enabled=true",
        "test.group1.subgroup1.enabled=false",
        "test.group1.subgroup2.enabled=true",
        "test.group2.enabled=false",
        "test.group2.subgroup1.enabled=false",
        "test.group2.subgroup2.enabled=true",
        "test.group2.subgroup3.enabled=false"
    }
)
public class HierarchicalPropertiesEvaluatorTest {
    @Autowired
    Environment environment;

    @Test
    public void testEvaluator() {
        Assert.assertFalse(HierarchicalPropertiesEvaluator.evaluate(environment, "test.group1", "test.group1.subgroup1"));
        Assert.assertTrue(HierarchicalPropertiesEvaluator.evaluate(environment, "test.group1", "test.group1.subgroup2"));
        Assert.assertFalse(HierarchicalPropertiesEvaluator.evaluate(environment, "test.group2", "test.group2.subgroup1"));
        Assert.assertTrue(HierarchicalPropertiesEvaluator.evaluate(environment, "test.group2", "test.group2.subgroup2"));
        Assert.assertFalse(HierarchicalPropertiesEvaluator.evaluate(environment, "test.group2", "test.group2.subgroup3"));
    }

    @Configuration
    static class TestConfiguration {
    }
}
