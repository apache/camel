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
     * Classes or packages outside the code under test whose time reads are rewritten too, without an escape report, for
     * example {@code org.apache.camel.support.MonotonicClock} or {@code org.apache.camel.util.StopWatch} of Camel core.
     * An entry whose last segment starts with an upper case letter is a class (with its nested classes), else a
     * package.
     * <p>
     * This is a stop-gap: Camel core reads {@code System} directly, and the rewriting of core classes stays for the
     * rest of the test JVM (outside a run the rewritten calls read the real clock). The system property
     * {@code dst.alsoRewrite} (comma separated) adds entries without changing the test.
     */
    String[] alsoRewrite() default {};
}
