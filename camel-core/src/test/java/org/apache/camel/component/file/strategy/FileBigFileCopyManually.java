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
package org.apache.camel.component.file.strategy;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * A manual test by copying a big file into the target/big/in folder and check
 * that it is copied completly, eg its file size is the same as the original file
 *
 * @version $Revision$
 */
public class FileBigFileCopyManually extends ContextTestSupport {

    public void testCopyBigFile() throws Exception {
        deleteDirectory("target/big/");
        createDirectory("target/big/in");

        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/big/out/bigfile.dat");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/big/in?noop=true&readLock=changed").to("file://target/big/out", "mock:out");
            }
        };
    }
}
