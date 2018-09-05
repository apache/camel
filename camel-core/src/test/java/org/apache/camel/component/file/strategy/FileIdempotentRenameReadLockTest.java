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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version
 */
public class FileIdempotentRenameReadLockTest extends FileIdempotentReadLockTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/changed/in?initialDelay=0&delay=10&readLockCheckInterval=100&readLock=idempotent-rename&idempotentRepository=#myRepo")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // we are in progress
                            int size = myRepo.getCacheSize();
                            assertTrue(size == 1 || size == 2);
                        }
                    })
                    .to("mock:result");
            }
        };
    }
}
