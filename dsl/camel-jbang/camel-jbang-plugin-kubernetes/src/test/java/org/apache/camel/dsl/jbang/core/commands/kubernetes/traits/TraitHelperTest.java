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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.util.Arrays;
import java.util.Properties;

import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TraitHelperTest {

    @Test
    public void mergeTraitsTest() {
        String[] defaultGroup = new String[] {
                "container.port=8080",
                "container.port-name=custom",
                "container.service-port-name=custom-port",
                "container.service-port=8443" };
        String[] overridesGroup = new String[] {
                "container.port=80",
                "container.service-port=443",
                "container.image-pull-policy=IfNotPresent" };
        String[] result = TraitHelper.mergeTraits(overridesGroup, defaultGroup);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(5, result.length);
        Assertions.assertEquals("container.port=80", result[0]);
        Assertions.assertEquals("container.service-port-name=custom-port", result[4]);

        String[] resultEmptyDefault = TraitHelper.mergeTraits(overridesGroup, new String[0]);
        Assertions.assertNotNull(resultEmptyDefault);
        Assertions.assertEquals(3, resultEmptyDefault.length);
        Assertions.assertArrayEquals(overridesGroup, resultEmptyDefault);

        String[] resultNull = TraitHelper.mergeTraits(null);
        Assertions.assertNotNull(resultNull);
    }

    @Test
    public void extractTraitsFromAnnotationsTest() {
        String[] annotations = new String[] {
                "trait.camel.apache.org/container.port=8080",
                "trait.camel.apache.org/container.port-name=custom",
                "camel.apache.org/name=MyRoute" };
        String[] result = TraitHelper.extractTraitsFromAnnotations(annotations);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
        Assertions.assertEquals("container.port-name=custom", result[1]);

        String[] resultEmpty = TraitHelper.extractTraitsFromAnnotations(new String[0]);
        Assertions.assertNotNull(resultEmpty);

        String[] resultNull = TraitHelper.extractTraitsFromAnnotations(null);
        Assertions.assertNotNull(resultNull);
    }

    @Test
    public void extractTraitsFromPropertiesTest() {
        Properties properties = new Properties();
        properties.setProperty("camel.jbang.trait.container.port", "8080");
        properties.setProperty("camel.jbang.trait.container.port-name", "custom");
        properties.setProperty("camel.jbang.name", "MyRoute");
        String[] result = TraitHelper.extractTraitsFromProperties(properties);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
        Assertions.assertTrue(Arrays.asList(result).contains("container.port-name=custom"));

        String[] resultEmpty = TraitHelper.extractTraitsFromProperties(new Properties());
        Assertions.assertNotNull(resultEmpty);

        String[] resultNull = TraitHelper.extractTraitsFromProperties(null);
        Assertions.assertNotNull(resultNull);
    }

    @Test
    public void parseTraitsTest() {
        String[] traits = new String[] {
                "custom.property=custom",
                "container.port=8080",
                "container.port-name=custom" };
        Traits traitsSpec = TraitHelper.parseTraits(traits);
        Assertions.assertNotNull(traitsSpec);
        Assertions.assertEquals(8080L, traitsSpec.getContainer().getPort());
        Assertions.assertNotNull(traitsSpec.getAddons().get("custom"));

        Traits traitsSpecEmpty = TraitHelper.parseTraits(new String[0]);
        Assertions.assertNotNull(traitsSpecEmpty);

        Traits traitsSpecNull = TraitHelper.parseTraits(null);
        Assertions.assertNotNull(traitsSpecNull);
    }
}
