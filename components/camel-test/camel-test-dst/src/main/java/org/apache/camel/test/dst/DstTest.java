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

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the test method once per seed, each time with a new {@link Dst} run as parameter. A failing seed is reported
 * with its step trace and the way to replay it ({@link DstSeed} or {@code -Ddst.seed=<seed>}).
 * <p>
 * System properties override the attributes: {@code dst.seeds}, {@code dst.firstSeed}, {@code dst.maxFailures},
 * {@code dst.seed} (a comma separated list of seeds to replay).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(DstExtension.class)
public @interface DstTest {

    /** Number of seeds to run. */
    int seeds() default 1000;

    /** The first seed; the seeds are firstSeed, firstSeed + 1, ... */
    long firstSeed() default 1;

    /** Stop after this many failing seeds. */
    int maxFailures() default 1;

    /** Step budget of one run (a run that needs more is reported as a livelock). */
    long maxSteps() default 1_000_000;
}
