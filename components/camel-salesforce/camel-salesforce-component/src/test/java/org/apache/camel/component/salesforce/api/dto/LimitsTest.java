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
package org.apache.camel.component.salesforce.api.dto;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.dto.Limits.Usage;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class LimitsTest {

    @Test
    public void shouldBeKnownIfDefined() {
        assertFalse("Known usage must not declare itself as unknown", new Usage(1, 2).isUnknown());
    }

    @Test
    public void shouldDeserializeFromSalesforceGeneratedJSON() throws JsonProcessingException, IOException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final Object read = mapper.readerFor(Limits.class).readValue(LimitsTest.class.getResource("/org/apache/camel/component/salesforce/api/dto/limits.json"));

        assertThat("Limits should be parsed from JSON", read, instanceOf(Limits.class));

        final Limits limits = (Limits)read;

        final Usage dailyApiRequests = limits.getDailyApiRequests();
        assertFalse("Should have some usage present", dailyApiRequests.isUnknown());
        assertFalse("Per application usage should be present", dailyApiRequests.getPerApplicationUsage().isEmpty());
        assertNotNull("'Camel Salesman' application usage should be present", dailyApiRequests.forApplication("Camel Salesman"));
    }

    @Test
    public void shouldDeserializeWithUnsupportedKeys() throws JsonProcessingException, IOException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final Limits withUnsupported = mapper.readerFor(Limits.class).readValue("{\"Camel-NotSupportedKey\": {\"Max\": 200,\"Remaining\": 200}}");

        assertNotNull(withUnsupported);
        assertNotNull(withUnsupported.forOperation("Camel-NotSupportedKey"));
    }

    @Test
    public void shouldSupportGettingAllDefinedUsages() throws IntrospectionException {
        final BeanInfo beanInfo = Introspector.getBeanInfo(Limits.class);

        final PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();

        final Set<String> found = new HashSet<>();
        for (final PropertyDescriptor descriptor : propertyDescriptors) {
            found.add(descriptor.getName());
        }

        final Set<String> defined = Arrays.stream(Limits.Operation.values()).map(Limits.Operation::name).map(Introspector::decapitalize).collect(Collectors.toSet());

        defined.removeAll(found);

        assertThat("All operations declared in Operation enum should have it's corresponding getter", defined, is(Collections.emptySet()));
    }

    @Test
    public void usageShouldBeUnknownIfUnknown() {
        assertTrue("Unknown usage must declare itself as such", Usage.UNKNOWN.isUnknown());
    }
}
