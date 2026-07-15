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
package org.apache.camel.component.file.strategy;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that idempotent read lock correctly removes the key on rollback when preMove is used. Before the fix, the
 * release key was computed from the post-preMove path, which didn't match the acquire key (original path), so the
 * original key leaked in the repository and same-named files were never consumed again.
 */
public class FileIdempotentReadLockPreMoveTest extends ContextTestSupport {

    final MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myRepo", myRepo);
        return jndi;
    }

    @Test
    public void testIdempotentReadLockKeyRemovedOnRollbackWithPreMove() throws Exception {
        assertEquals(0, myRepo.getCacheSize());

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertTrue(notify.matches(10, TimeUnit.SECONDS));

        // the route throws an exception so the file is rolled back
        // with readLockRemoveOnRollback=true (default), the key must be removed
        // from the repository so the file can be retried
        assertEquals(0, myRepo.getCacheSize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());

                from(fileUri(
                        "?initialDelay=0&delay=10&readLock=idempotent&idempotentRepository=#myRepo&preMove=work&moveFailed=error"))
                        .throwException(new IllegalStateException("Forced failure"));
            }
        };
    }
}
