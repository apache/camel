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
package org.apache.camel.test.dst;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The packages of the code under test: their time reads (see {@link DstClock}) read the virtual clock, and their escape
 * points are reported before the first seed runs. Put it on the test class (it also registers the class level part of
 * {@link DstExtension}: the summary and the escape report).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DstExtension.class)
public @interface DstInstrument {

    /** The packages of the code under test (with their sub packages). */
    String[] value();

    /**
     * Classes or packages outside the code under test whose time reads, sleeps and blocking waits are rewritten too,
     * without an escape report, for example {@code org.apache.camel.support.ResetableClock} of Camel core or a library
     * the component calls. An entry whose last segment starts with an upper case letter is a class (with its nested
     * classes), else a package.
     * <p>
     * Not needed for the Camel core code that waits, times tasks or throttles ({@code StopWatch},
     * {@code MonotonicClock}, the synchronous redelivery, {@code ForegroundTask}, the wait loop of the graceful
     * shutdown, the clocks of the throttlers): it reads the clock and sleeps through
     * {@code org.apache.camel.util.TimeSource}, whose provider is the virtual clock while a run is open. The permit
     * wait of {@code org.apache.camel.processor.TotalRequestsThrottler} is a {@code DelayQueue.take()}, which is not a
     * clock read: list that class when a world uses it. The rewriting stays for the rest of the test JVM (outside a run
     * the rewritten calls read the real clock). The system property {@code dst.alsoRewrite} (comma separated) adds
     * entries without changing the test.
     */
    String[] alsoRewrite() default {};

    /**
     * Strict mode (the default): a seed fails when the code under test reaches, at runtime, an escape point the harness
     * does not control (an own thread or executor, the common pool, a Reactor scheduler, a wait the harness cannot run
     * the loop for, a task submitted, a virtual clock read or a sleep from a thread outside the harness). Without it
     * such escapes are only listed in the summary. The system property {@code dst.strict} overrides it.
     */
    boolean strict() default true;

    /**
     * Escapes that are allowed in strict mode: an escape whose description contains one of these strings is only
     * recorded, for example {@code "EventHubsConsumer.doStop"}. Say in a comment why each one cannot change the result.
     */
    String[] allowEscapes() default {};
}
