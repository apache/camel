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
package org.apache.camel.management;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Endpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two endpoints that only differ in a masked secret are added at the same time: the endpoint recorded as the owner of
 * the MBean must be the one whose MBean was registered.
 */
@DisabledOnOs(OS.AIX)
class ManagedAddEndpointMaskedSecretRaceTest extends ManagementTestSupport {

    @Test
    void testAddEndpointsWithTheSameNameAtTheSameTime() throws Exception {
        context.getManagementStrategy().getManagementAgent().setRegisterAlways(true);
        ObjectName on = getCamelObjectName(TYPE_ENDPOINT, "stub://race\\?password=xxxxxx");

        // the first time the thread adding endpoint x asks whether the name is registered, it waits for the test after
        // the answer, so that endpoint y can be added meanwhile
        MBeanServer server = getMBeanServer();
        AtomicReference<Thread> adder = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MBeanServer gated = (MBeanServer) Proxy.newProxyInstance(MBeanServer.class.getClassLoader(),
                new Class<?>[] { MBeanServer.class }, (proxy, method, args) -> {
                    Object answer;
                    try {
                        answer = method.invoke(server, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if ("isRegistered".equals(method.getName()) && on.equals(args[0])
                            && Thread.currentThread() == adder.get() && entered.getCount() > 0) {
                        entered.countDown();
                        assertTrue(release.await(20, TimeUnit.SECONDS), "The test should release the adder");
                    }
                    return answer;
                });
        context.getManagementStrategy().getManagementAgent().setMBeanServer(gated);
        try {
            AtomicReference<Endpoint> x = new AtomicReference<>();
            Thread threadX = new Thread(() -> x.set(context.getEndpoint("stub:race?password=x")), "adder-x");
            adder.set(threadX);
            threadX.start();
            assertTrue(entered.await(20, TimeUnit.SECONDS), "Endpoint x should be added");

            AtomicReference<Endpoint> y = new AtomicReference<>();
            Thread threadY = new Thread(() -> y.set(context.getEndpoint("stub:race?password=y")), "adder-y");
            threadY.start();
            // endpoint y is added meanwhile, or waits for endpoint x
            await().atMost(20, TimeUnit.SECONDS).until(() -> !threadY.isAlive()
                    || threadY.getState() == Thread.State.WAITING || threadY.getState() == Thread.State.BLOCKED);
            release.countDown();
            threadX.join(20000);
            threadY.join(20000);
            assertFalse(threadX.isAlive(), "Endpoint x should be added");
            assertFalse(threadY.isAlive(), "Endpoint y should be added");
            assertTrue(server.isRegistered(on), "Should be registered");

            // endpoint x was the first to ask, so the MBean is the one of endpoint x, and removing endpoint y keeps it
            context.removeEndpoint(y.get());
            assertTrue(server.isRegistered(on), "The MBean of endpoint x should still be registered");
            assertEquals("Started", server.getAttribute(on, "State"),
                    "The MBean should be the one of endpoint x, which is still in use");

            context.removeEndpoint(x.get());
            assertFalse(server.isRegistered(on), "Should no longer be registered");
        } finally {
            release.countDown();
            context.getManagementStrategy().getManagementAgent().setMBeanServer(server);
        }
    }
}
