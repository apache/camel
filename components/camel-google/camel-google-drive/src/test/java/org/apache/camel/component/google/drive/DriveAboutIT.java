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
package org.apache.camel.component.google.drive;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveAboutApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for com.google.api.services.drive.Drive$About APIs.
 */
@EnabledIf(value = "org.apache.camel.component.google.drive.AbstractGoogleDriveTestSupport#hasCredentials",
           disabledReason = "Google Drive credentials were not provided")
public class DriveAboutIT extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DriveAboutIT.class);
    private static final String PATH_PREFIX
            = GoogleDriveApiCollection.getCollection().getApiName(DriveAboutApiMethod.class).getName();

    @Test
    public void testGet() {
        final com.google.api.services.drive.model.About result = requestBody("direct://GET", null);

        assertNotNull(result, "get result");
        LOG.debug("get: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for get
                from("direct://GET")
                        .to("google-drive://" + PATH_PREFIX + "/get");

            }
        };
    }
}
