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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify exclusive read by for manual testing.
 */
public class FileExclusiveReadManuelTest extends ContextTestSupport {

    private String fileUrl = "file://target/exclusiveread?readLock=fileLock&initialDelay=0&delay=10";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/exclusiveread");
        disableJMX();
        super.setUp();
    }

    public void testManually() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // this is used for manual testing where you can copy/lock files etc. while this test runs
        //mock.setSleepForEmptyTest(10 * 1000L);
        mock.setSleepForEmptyTest(100);
        mock.expectedMessageCount(0);

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).to("mock:result");
            }
        };
    }

}