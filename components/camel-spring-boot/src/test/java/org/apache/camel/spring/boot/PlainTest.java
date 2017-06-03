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
package org.apache.camel.spring.boot;

import org.apache.camel.CamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest
public class PlainTest {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired(required = false)
    CamelContext camelContext;

    // turn off Camel auto configuration which should not inject a CamelContext
    @Configuration
    @EnableAutoConfiguration(exclude = CamelAutoConfiguration.class)
    public static class MyConfiguration {
        // empty
    }

    @Test
    public void testPlain() {
        Assert.assertNull("Should not auto configure CamelContext", camelContext);
        Assert.assertEquals("Should not auto configure CamelAutoConfiguration", 0, applicationContext.getBeanNamesForType(CamelAutoConfiguration.class).length);
    }
}
