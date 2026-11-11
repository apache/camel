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
package org.apache.camel.impl;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultDumpRoutesStrategyOutputTest extends ContextTestSupport {

    @Test
    public void testDirectoryOutputAfterFileOutput() throws Exception {
        DumpRoutesStrategy drs = context.getCamelContextExtension().getContextPlugin(DumpRoutesStrategy.class);
        drs.setInclude("*");
        drs.setLog(false);

        File fileDir = testDirectory().resolve("first").toFile();
        File folder = testDirectory().resolve("second").toFile();

        // first dump to a file
        drs.setOutput(new File(fileDir, "MyTest-test.xml").getPath());
        drs.dumpRoutes("xml");

        // then dump to a directory, which must not reuse the file name from the first dump
        drs.setOutput(folder.getPath());
        drs.dumpRoutes("xml");

        assertThat(folder.list()).containsExactly("dump1.xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo")
                        .to("mock:result");
            }
        };
    }
}
