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

import com.google.api.services.drive.model.File;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveFilesApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for com.google.api.services.drive.Drive$Files APIs.
 */
@EnabledIf(value = "org.apache.camel.component.google.drive.AbstractGoogleDriveTestSupport#hasCredentials",
           disabledReason = "Google Drive credentials were not provided")
public class FileConverterIT extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileConverterIT.class);
    private static final String PATH_PREFIX
            = GoogleDriveApiCollection.getCollection().getApiName(DriveFilesApiMethod.class).getName();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/convertertest");
        super.setUp();
    }

    @Test
    public void testFileConverter() throws Exception {
        template.sendBodyAndHeader("file://target/convertertest", "Hello!", "CamelFileName", "greeting.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        File file = mock.getReceivedExchanges().get(0).getIn().getBody(com.google.api.services.drive.model.File.class);

        assertEquals("Hello!", context.getTypeConverter().convertTo(String.class, mock.getReceivedExchanges().get(0), file));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file://target/convertertest?noop=true")
                        .convertBodyTo(File.class)
                        .to("google-drive://drive-files/insert?inBody=content")
                        .to("mock:result");
            }
        };
    }
}
