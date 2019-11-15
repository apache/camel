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
package org.apache.camel.dataformat.tarfile;

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

public class TarFileSplitAndDeleteTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/testDeleteTarFileWhenUnmarshalWithDataFormat");
        deleteDirectory("target/testDeleteTarFileWhenUnmarshalWithSplitter");
        super.setUp();
    }

    @Test
    public void testDeleteTarFileWhenUnmarshalWithDataFormat() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("file://target/" + "testDeleteTarFileWhenUnmarshalWithDataFormat").whenDone(1).create();
        getMockEndpoint("mock:end").expectedMessageCount(3);
        String tarFile = createTarFile("testDeleteTarFileWhenUnmarshalWithDataFormat");

        assertMockEndpointsSatisfied();

        notify.matchesMockWaitTime();

        // the original file should have been deleted
        assertFalse("File should been deleted", new File(tarFile).exists());
    }

    @Test
    public void testDeleteTarFileWhenUnmarshalWithSplitter() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("file://target/" + "testDeleteTarFileWhenUnmarshalWithSplitter").whenDone(1).create();
        getMockEndpoint("mock:end").expectedMessageCount(3);
        String tarFile = createTarFile("testDeleteTarFileWhenUnmarshalWithSplitter");

        assertMockEndpointsSatisfied();

        notify.matchesMockWaitTime();

        // the original file should have been deleted,
        assertFalse("File should been deleted", new File(tarFile).exists());
    }


    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                TarFileDataFormat dataFormat = new TarFileDataFormat();
                dataFormat.setUsingIterator(true);

                from("file://target/testDeleteTarFileWhenUnmarshalWithDataFormat?delete=true")
                        .unmarshal(dataFormat)
                        .split(bodyAs(Iterator.class)).streaming()
                        .convertBodyTo(String.class)
                        .to("mock:end")
                        .end();

                from("file://target/testDeleteTarFileWhenUnmarshalWithSplitter?delete=true")
                        .split(new TarSplitter()).streaming()
                        .convertBodyTo(String.class)
                        .to("mock:end")
                        .end();
            }
        };
    }

    private String createTarFile(String folder) throws IOException {
        Path source = Paths.get("src/test/resources/data/tarfile3.tar");
        Path target = Paths.get("target" + File.separator + folder + File.separator + "data.tar");
        target.toFile().getParentFile().mkdirs();
        Path copy = Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return copy.toAbsolutePath().toString();
    }
}
