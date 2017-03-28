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
package org.apache.camel.rx;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.Test;
import rx.Observable;

public class ToObservableUoWTest extends RxTestSupport {

    @Override
    public void init() throws Exception {
        FileUtil.removeDir(new File("target/foo"));
        super.init();
    }

    @Test
    public void testConsumeUoW() throws Exception {
        final MockEndpoint mockEndpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        Observable<Message> observable = reactiveCamel.toObservable("file://target/foo?move=done");
        observable.subscribe(message -> {
            String body = message.getBody(String.class);
            producerTemplate.sendBody("mock:results", body);
        });

        producerTemplate.sendBodyAndHeader("file://target/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");
        producerTemplate.sendBodyAndHeader("file://target/foo", "Bye World", Exchange.FILE_NAME, "bye.txt");

        mockEndpoint.expectedFileExists("target/foo/done/hello.txt");
        mockEndpoint.expectedFileExists("target/foo/done/bye.txt");

        mockEndpoint.assertIsSatisfied();
    }
}
