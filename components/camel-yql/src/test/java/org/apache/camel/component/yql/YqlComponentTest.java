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
package org.apache.camel.component.yql;

import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.yql.configuration.YqlConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class YqlComponentTest extends CamelTestSupport {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("yql://query?format=json")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testCreateProducer() throws Exception {
        // given
        final Endpoint yqlEndpoint = context.getEndpoint("yql://query?format=json");

        // when
        final Producer producer = yqlEndpoint.createProducer();

        // then
        assertNotNull(producer);
        assertTrue(producer instanceof YqlProducer);
    }

    @Test
    public void testConfigurationSetup() {
        // given
        final YqlEndpoint yqlEndpoint = (YqlEndpoint) context.getEndpoint("yql://query?format=xml&callback=yqlCallback&diagnostics=true"
            + "&debug=true&https=false&throwExceptionOnFailure=false&jsonCompat=new");

        // when
        final YqlConfiguration yqlConfiguration = yqlEndpoint.getConfiguration();

        // then
        assertNotNull(yqlConfiguration);
        assertEquals("query", yqlConfiguration.getQuery());
        assertEquals("xml", yqlConfiguration.getFormat());
        assertEquals("yqlCallback", yqlConfiguration.getCallback());
        assertTrue(yqlConfiguration.isDebug());
        assertTrue(yqlConfiguration.isDiagnostics());
        assertFalse(yqlConfiguration.isHttps());
        assertFalse(yqlConfiguration.isThrowExceptionOnFailure());
    }

    @Test
    public void testConfigurationSetupDefault() {
        // given
        final YqlEndpoint yqlEndpoint = (YqlEndpoint) context.getEndpoint("yql://query");

        // when
        final YqlConfiguration yqlConfiguration = yqlEndpoint.getConfiguration();

        // then
        assertNotNull(yqlConfiguration);
        assertEquals("query", yqlConfiguration.getQuery());
        assertEquals("json", yqlConfiguration.getFormat());
        assertNull(yqlConfiguration.getCallback());
        assertNull(yqlConfiguration.getCrossProduct());
        assertFalse(yqlConfiguration.isDiagnostics());
        assertFalse(yqlConfiguration.isDebug());
        assertNull(yqlConfiguration.getEnv());
        assertNull(yqlConfiguration.getJsonCompat());
        assertTrue(yqlConfiguration.isThrowExceptionOnFailure());
        assertTrue(yqlConfiguration.isHttps());
    }

    @Test
    public void testCreateConsumer() throws Exception {
        // then
        thrown.expect(UnsupportedOperationException.class);

        // given
        final Endpoint yqlEndpoint = context.getEndpoint("yql://query?format=json");

        // when
        yqlEndpoint.createConsumer(null);
    }
}
