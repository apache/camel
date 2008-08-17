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

import java.io.File;

import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FileDeleteRouteTest extends FileRouteTest {
    @Override
    protected void setUp() throws Exception {
        targetdir = "target/test-delete-inbox";
        params = "?consumer.delay=1000&delete=true&consumer.recursive=true";
        super.setUp();
    }

    @Override
    public void testFileRoute() throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(expectedBody);
        result.setResultWaitTime(5000);

        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);
        Thread.sleep(4000);

        result.assertIsSatisfied();

        for (String lockName : recorder.getLocks()) {
            File lock = new File(lockName);
            assertFalse(lock.exists());
        }
    }

}
