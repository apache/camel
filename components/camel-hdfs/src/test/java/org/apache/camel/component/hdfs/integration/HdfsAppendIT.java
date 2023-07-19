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
package org.apache.camel.component.hdfs.integration;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.hdfs.v2.services.HDFSService;
import org.apache.camel.test.infra.hdfs.v2.services.HDFSServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HdfsAppendIT extends CamelTestSupport {
    @RegisterExtension
    public static HDFSService service = HDFSServiceFactory.createSingletonService(AvailablePortFinder.getNextAvailable());

    private static final Logger LOG = LoggerFactory.getLogger(HdfsAppendIT.class);

    private static final int ITERATIONS = 10;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        Configuration conf = new Configuration();
        if (SystemUtils.IS_OS_MAC) {
            conf.addResource("hdfs-mac-test.xml");
        } else {
            conf.addResource("hdfs-test.xml");
        }
        String path = String.format("hdfs://%s:%d/tmp/test/test-camel-simple-write-file1", service.getHDFSHost(),
                service.getPort());

        Path file = new Path(path);
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        if (fs.exists(file)) {
            fs.delete(file, true);
        }
        try (FSDataOutputStream out = fs.create(file)) {
            for (int i = 0; i < 10; ++i) {
                out.write("PIPPO".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    public void testAppend() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start1")
                        .toF("hdfs://%s:%d/tmp/test/test-camel-simple-write-file1?append=true&fileSystemType=HDFS",
                                service.getHDFSHost(), service.getPort());
            }
        });
        startCamelContext();

        for (int i = 0; i < 10; ++i) {
            template.sendBody("direct:start1", "PIPPQ");
        }

        Configuration conf = new Configuration();
        String path = String.format("hdfs://%s:%d/tmp/test/test-camel-simple-write-file1", service.getHDFSHost(),
                service.getPort());
        Path file = new Path(path);
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        int ret = 0;
        try (FSDataInputStream in = fs.open(file)) {
            byte[] buffer = new byte[5];
            for (int i = 0; i < 20; ++i) {
                assertEquals(5, in.read(buffer));
                LOG.info("> {}", new String(buffer));
            }
            ret = in.read(buffer);
        }
        assertEquals(-1, ret);
    }

    @Test
    public void testAppendWithDynamicFileName() throws Exception {

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start1").toF("hdfs://%s:%d/tmp/test-dynamic/?append=true&fileSystemType=HDFS",
                        service.getHDFSHost(), service.getPort());
            }
        });
        startCamelContext();

        for (int i = 0; i < ITERATIONS; ++i) {
            template.sendBodyAndHeader("direct:start1", "HELLO", Exchange.FILE_NAME, "camel-hdfs.log");
        }

        Configuration conf = new Configuration();
        String path = String.format("hdfs://%s:%d/tmp/test-dynamic/camel-hdfs.log", service.getHDFSHost(),
                service.getPort());

        Path file = new Path(path);
        FileSystem fs = FileSystem.get(file.toUri(), conf);
        int ret = 0;
        try (FSDataInputStream in = fs.open(file)) {
            byte[] buffer = new byte[5];
            for (int i = 0; i < ITERATIONS; ++i) {
                assertEquals(5, in.read(buffer));
                LOG.info("> {}", new String(buffer));
            }
            ret = in.read(buffer);
        }
        assertEquals(-1, ret);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(250);
        Configuration conf = new Configuration();
        Path dir = new Path(String.format("hdfs://%s:%d/tmp/test", service.getHDFSHost(), service.getPort()));
        FileSystem fs = FileSystem.get(dir.toUri(), conf);
        fs.delete(dir, true);
        dir = new Path(String.format("hdfs://%s:%d/tmp/test-dynamic", service.getHDFSHost(), service.getPort()));
        fs.delete(dir, true);
    }
}
