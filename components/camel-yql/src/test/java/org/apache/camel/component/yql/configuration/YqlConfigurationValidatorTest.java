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
package org.apache.camel.component.yql.configuration;

import org.apache.camel.component.yql.exception.YqlException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class YqlConfigurationValidatorTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testValidQuery() {
        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();
        yqlConfiguration.setQuery("select * from ...");
        yqlConfiguration.setFormat("json");
        yqlConfiguration.setCrossProduct("optimized");
        yqlConfiguration.setJsonCompat("new");

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);

        // then
        // no exception
    }

    @Test
    public void testMissingQuery() {
        // then
        thrown.expect(YqlException.class);
        thrown.expectMessage("<query> is not present or not valid!");

        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);
    }

    @Test
    public void testJsonFormat() {
        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();
        yqlConfiguration.setQuery("query");
        yqlConfiguration.setFormat("json");

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);

        // then
        // no exception
    }

    @Test
    public void testXmlFormat() {
        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();
        yqlConfiguration.setQuery("query");
        yqlConfiguration.setFormat("xml");

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);

        // then
        // no exception
    }

    @Test
    public void testWrongFormat() {
        // then
        thrown.expect(YqlException.class);
        thrown.expectMessage("<format> is not valid!");

        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();
        yqlConfiguration.setQuery("query");
        yqlConfiguration.setFormat("format");

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);
    }

    @Test
    public void testWrongCrossProduct() {
        // then
        thrown.expect(YqlException.class);
        thrown.expectMessage("<crossProduct> is not valid!");

        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();
        yqlConfiguration.setQuery("query");
        yqlConfiguration.setFormat("xml");
        yqlConfiguration.setCrossProduct("optimizedddd");

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);
    }

    @Test
    public void testWrongJsonCompat() {
        // then
        thrown.expect(YqlException.class);
        thrown.expectMessage("<jsonCompat> is not valid!");

        // given
        final YqlConfiguration yqlConfiguration = new YqlConfiguration();
        yqlConfiguration.setQuery("query");
        yqlConfiguration.setFormat("xml");
        yqlConfiguration.setCrossProduct("optimized");
        yqlConfiguration.setJsonCompat("neww");

        // when
        YqlConfigurationValidator.validateProperties(yqlConfiguration);
    }
}
