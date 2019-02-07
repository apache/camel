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
package org.apache.camel.maven;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelSalesforceMojoOverrideFieldsTest {

    @Test
    public void shouldDisallowFieldsOverrides() {
        // given
        int properCountExceptions = 9;
        final List<ImmutablePair<String, FieldTypeOverride>> invalidOverrides = new LinkedList<>();
        invalidOverrides.add(ImmutablePair.of("datetime", new FieldTypeOverride("Account", "AccountSource", "String[]")));
        invalidOverrides.add(ImmutablePair.of("time", new FieldTypeOverride("Account", "AccountSource", "String[]")));
        invalidOverrides.add(ImmutablePair.of("date", new FieldTypeOverride("Account", "AccountSource", "String[]")));
        invalidOverrides.add(ImmutablePair.of("g", new FieldTypeOverride("Account", "AccountSource", "String[]")));
        invalidOverrides.add(ImmutablePair.of("float", new FieldTypeOverride("Account", "AccountSource", "String")));
        invalidOverrides.add(ImmutablePair.of("decimal", new FieldTypeOverride("Account", "AccountSource", String.class.getName())));
        invalidOverrides.add(ImmutablePair.of("picklist", new FieldTypeOverride("Account", "AccountSource", String.class.getName() + "[]")));
        invalidOverrides.add(ImmutablePair.of("picklist", new FieldTypeOverride("Account", "AccountSource", ZonedDateTime.class.getName())));
        invalidOverrides.add(ImmutablePair.of("multipicklist", new FieldTypeOverride("Account", "AccountSource", OffsetTime.class.getName())));

        // when
        int countExceptionsThrown = 0;
        for (ImmutablePair<String, FieldTypeOverride> invalidOverride : invalidOverrides) {
            try {
                GenerateMojo.isOverrideAllowed(invalidOverride.getLeft(), invalidOverride.getRight());
            } catch (IllegalArgumentException e) {
                countExceptionsThrown++;
            }
        }

        // then
        Assert.assertEquals(properCountExceptions, countExceptionsThrown);
    }

    @Test(expected = Test.None.class)
    public void shouldAllowFieldsOverrides() {
    // given
        final List<ImmutablePair<String, FieldTypeOverride>> validOverrides = new LinkedList<>();
        validOverrides.add(ImmutablePair.of("datetime", new FieldTypeOverride("Account", "AccountSource", OffsetTime.class.getName())));
        validOverrides.add(ImmutablePair.of("datetime", new FieldTypeOverride("Account", "AccountSource", LocalDate.class.getName())));
        validOverrides.add(ImmutablePair.of("time", new FieldTypeOverride("Account", "AccountSource", LocalDate.class.getName())));
        validOverrides.add(ImmutablePair.of("time", new FieldTypeOverride("Account", "AccountSource", ZonedDateTime.class.getName())));
        validOverrides.add(ImmutablePair.of("date", new FieldTypeOverride("Account", "AccountSource", OffsetTime.class.getName())));
        validOverrides.add(ImmutablePair.of("date", new FieldTypeOverride("Account", "AccountSource", ZonedDateTime.class.getName())));
        validOverrides.add(ImmutablePair.of("g", new FieldTypeOverride("Account", "AccountSource", LocalDate.class.getName())));
        validOverrides.add(ImmutablePair.of("picklist", new FieldTypeOverride("Account", "AccountSource", String.class.getName())));
        validOverrides.add(ImmutablePair.of("multipicklist", new FieldTypeOverride("Account", "AccountSource", String.class.getName())));
        validOverrides.add(ImmutablePair.of("multipicklist", new FieldTypeOverride("Account", "AccountSource", String.class.getName() + "[]")));

    // when then
        for (ImmutablePair<String, FieldTypeOverride> validOverride : validOverrides) {
            GenerateMojo.isOverrideAllowed(validOverride.getLeft(), validOverride.getRight());
        }
    }



}
