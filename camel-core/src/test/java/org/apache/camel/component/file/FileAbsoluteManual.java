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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * A manual unit test you can run to test absolute paths works as expected.
 * <p/>
 * We do have other absolute tests that are run when running tests.
 *
 * @version $Revision$
 */
public class FileAbsoluteManual extends ContextTestSupport {

    public void testAbsolute() throws Exception {
        template.sendBodyAndHeader("file:///tmp/in", "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(3000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:///tmp/in")
                    .to("file:///tmp/out");
            }
        };
    }
}
