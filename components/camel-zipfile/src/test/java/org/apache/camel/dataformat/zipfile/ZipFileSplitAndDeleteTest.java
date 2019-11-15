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
package org.apache.camel.dataformat.zipfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class ZipFileSplitAndDeleteTest extends CamelTestSupport {


    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/testDeleteZipFileWhenUnmarshalWithDataFormat");
        deleteDirectory("target/testDeleteZipFileWhenUnmarshalWithSplitter");
        super.setUp();
    }

    @Test
    public void testDeleteZipFileWhenUnmarshalWithDataFormat() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("file://target/" + "testDeleteZipFileWhenUnmarshalWithDataFormat").whenDone(1).create();
        getMockEndpoint("mock:end").expectedMessageCount(2);
        String zipFile = createZipFile("testDeleteZipFileWhenUnmarshalWithDataFormat");

        assertMockEndpointsSatisfied();

        notify.matchesMockWaitTime();

        // the original file should have been deleted
        assertFalse("File should been deleted", new File(zipFile).exists());
    }

    @Test
    public void testDeleteZipFileWhenUnmarshalWithSplitter() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("file://target/" + "testDeleteZipFileWhenUnmarshalWithSplitter").whenDone(1).create();
        getMockEndpoint("mock:end").expectedMessageCount(2);
        String zipFile = createZipFile("testDeleteZipFileWhenUnmarshalWithSplitter");

        assertMockEndpointsSatisfied();

        notify.matchesMockWaitTime();

        // the original file should have been deleted,
        assertFalse("File should been deleted", new File(zipFile).exists());
    }


    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ZipFileDataFormat dataFormat = new ZipFileDataFormat();
                dataFormat.setUsingIterator(true);

                from("file://target/testDeleteZipFileWhenUnmarshalWithDataFormat?delete=true")
                        .unmarshal(dataFormat)
                        .split(bodyAs(Iterator.class)).streaming()
                        .convertBodyTo(String.class)
                        .to("mock:end")
                        .end();

                from("file://target/testDeleteZipFileWhenUnmarshalWithSplitter?delete=true")
                        .split(new ZipSplitter()).streaming()
                        .convertBodyTo(String.class)
                        .to("mock:end")
                        .end();
            }
        };
    }

    private String createZipFile(String folder) throws IOException {
        Path source = Paths.get("src/test/resources/data.zip");
        Path target = Paths.get("target" + File.separator + folder + File.separator + "data.zip");
        target.toFile().getParentFile().mkdirs();
        Path copy = Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return copy.toAbsolutePath().toString();
    }
}
