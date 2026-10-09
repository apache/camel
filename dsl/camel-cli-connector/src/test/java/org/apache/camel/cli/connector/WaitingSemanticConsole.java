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
package org.apache.camel.cli.connector;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.console.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

final class WaitingSemanticConsole extends AbstractDevConsole {
    final CountDownLatch entered;
    final CountDownLatch release = new CountDownLatch(1);
    final CountDownLatch interrupted = new CountDownLatch(1);
    final AtomicInteger calls = new AtomicInteger();

    WaitingSemanticConsole(int callers) {
        super("camel", "semantic-evaluate", "Test", "Test");
        entered = new CountDownLatch(callers);
    }

    @Override
    public Object call(DevConsole.MediaType mediaType, Map<String, Object> options) {
        return doCallJson(options);
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return "";
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        calls.incrementAndGet();
        entered.countDown();
        try {
            release.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            interrupted.countDown();
            Thread.currentThread().interrupt();
        }
        return new JsonObject(Map.of("status", "success", "value", options.getOrDefault("input", "answer")));
    }
}
