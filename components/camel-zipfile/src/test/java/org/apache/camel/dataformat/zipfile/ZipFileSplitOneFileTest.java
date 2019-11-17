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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class ZipFileSplitOneFileTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/zip-unmarshal");
        super.setUp();
    }

    @Test
    public void testZipFileUnmarshal() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.FILE_NAME_ONLY, "test.zip");
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World");

        createZipFile("Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ZipFileDataFormat zf = new ZipFileDataFormat();
                zf.setUsingIterator(true);

                from("file://target/zip-unmarshal?noop=true&include=.*zip")
                    .to("mock:input")
                    .unmarshal(zf)
                    .split(bodyAs(Iterator.class)).streaming()
                        .convertBodyTo(String.class)
                        .to("mock:end")
                    .end();
            }
        };
    }

    private void createZipFile(String content) throws IOException {
        String basePath = "target" + File.separator + "zip-unmarshal" + File.separator;
        File file = new File(basePath + "test.txt");
        file.getParentFile().mkdirs();

        try (FileWriter fw = new FileWriter(file);
             FileOutputStream fos = new FileOutputStream(basePath + "test.zip");
             ZipOutputStream zos = new ZipOutputStream(fos);
             FileInputStream fis = new FileInputStream(basePath + "test.txt")) {

            fw.write(content);
            fw.close();

            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);

            int len;
            byte[] buffer = new byte[1024];

            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }

            zos.closeEntry();
        }
    }
}
