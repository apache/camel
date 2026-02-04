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
package org.apache.camel.component.google.bigquery.unit;

import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GoogleBigQueryConnectionFactoryTest {

    @Test
    public void testProjectIdSetterGetter() {
        GoogleBigQueryConnectionFactory factory = new GoogleBigQueryConnectionFactory();

        assertNull(factory.getProjectId());

        factory.setProjectId("test-project-id");
        assertEquals("test-project-id", factory.getProjectId());
    }

    @Test
    public void testFluentApiForProjectId() {
        GoogleBigQueryConnectionFactory factory = new GoogleBigQueryConnectionFactory()
                .setProjectId("my-project")
                .setServiceAccountKeyFile("key.json")
                .setServiceURL("https://bigquery.googleapis.com");

        assertEquals("my-project", factory.getProjectId());
        assertEquals("key.json", factory.getServiceAccountKeyFile());
        assertEquals("https://bigquery.googleapis.com", factory.getServiceURL());
    }

    @Test
    public void testProjectIdResetsClient() {
        GoogleBigQueryConnectionFactory factory = new GoogleBigQueryConnectionFactory();

        // Set project ID - this should reset the cached client (if any)
        GoogleBigQueryConnectionFactory result = factory.setProjectId("project-a");

        // Verify fluent API returns the same factory
        assertEquals(factory, result);
        assertEquals("project-a", factory.getProjectId());

        // Change project ID - should reset and update
        factory.setProjectId("project-b");
        assertEquals("project-b", factory.getProjectId());
    }
}
