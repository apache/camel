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

package org.apache.camel.web.groovy;

import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

/**
 * 
 */
public class IdempotentConsumerDSLTest extends GroovyRendererTestSupport {

    public void testIdempotentConsumerAsync() throws Exception {
        String dsl = "from(\"direct:start\").idempotentConsumer(header(\"messageId\"), MemoryIdempotentRepository.memoryIdempotentRepository()).threads().to(\"mock:result\")";
        String[] importClasses = new String[] {"import org.apache.camel.processor.idempotent.*"};

        assertEquals(dsl, render(dsl, importClasses));
    }

    public void testIdempotentConsumerAsyncWithCacheSize() throws Exception {
        // drop the cache size
        String dsl = "from(\"direct:start\").idempotentConsumer(header(\"messageId\"), MemoryIdempotentRepository.memoryIdempotentRepository(200)).threads().to(\"mock:result\")";
        String expected = "from(\"direct:start\").idempotentConsumer(header(\"messageId\"), MemoryIdempotentRepository.memoryIdempotentRepository()).threads().to(\"mock:result\")";
        String[] importClasses = new String[] {"import org.apache.camel.processor.idempotent.*"};

        assertEquals(expected, render(dsl, importClasses));
    }

    public void testIdempotentConsumerEager() throws Exception {
        String dsl = "from(\"direct:start\").idempotentConsumer(header(\"messageId\"), MemoryIdempotentRepository.memoryIdempotentRepository(200)).eager(false).to(\"mock:result\")";
        String expected = "from(\"direct:start\").idempotentConsumer(header(\"messageId\"), MemoryIdempotentRepository.memoryIdempotentRepository()).eager(false).to(\"mock:result\")";
        String[] importClasses = new String[] {"import org.apache.camel.processor.idempotent.*"};

        assertEquals(expected, render(dsl, importClasses));
    }

}
