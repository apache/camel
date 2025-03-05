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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileConsumerTemplateIdempotentEagerTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConsumerTemplate() throws Exception {
        context.start();

        String uri = fileUri() + "?noop=true";

        template.sendBodyAndHeader(uri, "one", Exchange.FILE_NAME, "1.txt");
        template.sendBodyAndHeader(uri, "two", Exchange.FILE_NAME, "2.txt");

        Exchange e1 = consumer().receive(uri, 5000);
        Assertions.assertNotNull(e1);
        String b1 = e1.getMessage().getBody(String.class);
        Assertions.assertTrue("one".equals(b1) || "two".equals(b1), "Should either be one or two, was: " + b1);
        consumer().doneUoW(e1);

        FileEndpoint fe = context.getEndpoint(uri, FileEndpoint.class);
        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) fe.getIdempotentRepository();
        Assertions.assertEquals(1, repo.getCacheSize());

        Exchange e2 = consumer().receive(uri, 5000);
        Assertions.assertNotNull(e2);
        String b2 = e2.getMessage().getBody(String.class);
        Assertions.assertTrue("one".equals(b2) || "two".equals(b2), "Should either be one or two, was: " + b2);
        consumer().doneUoW(e2);
        Assertions.assertEquals(2, repo.getCacheSize());

        Assertions.assertNotEquals(b1, b2);
    }
}
