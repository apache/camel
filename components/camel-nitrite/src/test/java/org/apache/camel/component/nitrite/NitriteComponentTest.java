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
package org.apache.camel.component.nitrite;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.dizitart.no2.Document;
import org.junit.Test;

public class NitriteComponentTest extends AbstractNitriteTest {

    @Test
    public void testMultipleDatabases() throws Exception {
        MockEndpoint mockA = getMockEndpoint("mock:dbA");
        MockEndpoint mockB = getMockEndpoint("mock:dbB");
        MockEndpoint mockC = getMockEndpoint("mock:dbC");
        mockA.setExpectedMessageCount(1);
        mockB.setExpectedMessageCount(1);
        mockC.setExpectedMessageCount(1);


        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb() + ".a.db"),
                Document.createDocument("key1", "db_a")
        );
        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb() + ".b.db"),
                Document.createDocument("key1", "db_b")
        );
        template.sendBody(String.format("nitrite://%s?collection=collection2", tempDb() + ".c.db"),
                Document.createDocument("key1", "db_c")
        );

        mockA.assertIsSatisfied();
        mockB.assertIsSatisfied();
        mockC.assertIsSatisfied();
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                FileUtil.deleteFile(new File(tempDb() + ".a.db"));
                FileUtil.deleteFile(new File(tempDb() + ".b.db"));
                FileUtil.deleteFile(new File(tempDb() + ".c.db"));
                fromF("nitrite://%s?collection=collection", tempDb() + ".a.db")
                        .to("log:a")
                        .to("mock:dbA");
                fromF("nitrite://%s?collection=collection&username=login&password=secret", tempDb() + ".b.db")
                        .to("log:b")
                        .to("mock:dbB");
                fromF("nitrite://%s?collection=collection2&username=login2&password=s3cr3t", tempDb() + ".c.db")
                        .to("log:c")
                        .to("mock:dbC");
            }
        };
    }
}
