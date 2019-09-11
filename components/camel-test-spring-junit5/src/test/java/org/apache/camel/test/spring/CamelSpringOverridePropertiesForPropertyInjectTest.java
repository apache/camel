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
package org.apache.camel.test.spring;

import java.util.Properties;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.camel.test.spring.junit5.UseOverridePropertiesWithPropertiesComponent;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@CamelSpringTest
@ContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CamelSpringOverridePropertiesForPropertyInjectTest {

    private static final String EXPECTED_PROPERTY_VALUE = "The value is overriden";

    @Produce("direct:start-override-route")
    private ProducerTemplate start;

    @UseOverridePropertiesWithPropertiesComponent
    public static Properties override() {
        Properties answer = new Properties();
        answer.put("property.to.override", EXPECTED_PROPERTY_VALUE);
        return answer;
    }

    @Test
    public void testOverride() {
        String response = start.requestBody((Object)"ignored", String.class);

        assertThat(response, is(EXPECTED_PROPERTY_VALUE));
    }

}
