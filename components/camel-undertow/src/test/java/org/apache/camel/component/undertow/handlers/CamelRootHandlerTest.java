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
package org.apache.camel.component.undertow.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.RedirectHandler;
import org.junit.Assert;
import org.junit.Test;

public class CamelRootHandlerTest {

    private static final HttpHandler DEFAULT_HANDLER = new NotFoundHandler();

    @Test
    public void httpAndWsUnssupportedForTheSamePath() {

        final CamelRootHandler root = new CamelRootHandler(DEFAULT_HANDLER);

        final RedirectHandler httpHandler = new RedirectHandler("http://whereever");

        Assert.assertTrue(root.isEmpty());
        root.add("/app1", null, false, httpHandler);
        Assert.assertFalse(root.isEmpty());

        try {
            root.add("/app1", null, false, new CamelWebSocketHandler());
            Assert.fail(IllegalArgumentException.class.getName() + " expected");
        } catch (IllegalArgumentException expected) {
        }

        root.remove("/app1", null, false);

        Assert.assertTrue(root.isEmpty());

        /* now the other way round: register wsHandler and try to register httpHandler for the same path */
        root.add("/app2", null, false, new CamelWebSocketHandler());
        try {
            root.add("/app2", null, false, httpHandler);
            Assert.fail(IllegalArgumentException.class.getName() + " expected");
        } catch (IllegalArgumentException expected) {
        }

    }

    @Test
    public void countWsHandlerInstances() {

        final CamelRootHandler root = new CamelRootHandler(DEFAULT_HANDLER);
        Assert.assertTrue(root.isEmpty());

        root.add("/app1", null, false, new CamelWebSocketHandler());
        Assert.assertFalse(root.isEmpty());

        /* registering twice must work */
        root.add("/app1", null, false, new CamelWebSocketHandler());
        Assert.assertFalse(root.isEmpty());

        /* we have to remove twice for the root to become empty */
        root.remove("/app1", null, false);
        Assert.assertFalse(root.isEmpty());
        root.remove("/app1", null, false);
        Assert.assertTrue(root.isEmpty());

    }

}
