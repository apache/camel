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

/**
 * Describes a fake world of a component: put it on the world class (for example {@code SqsWorld}), in the test sources
 * of the component.
 * <p>
 * The worlds are test classes, so they never reach the component JAR. When the component is built, the
 * camel-package-maven-plugin reads this annotation from the test sources (not from the compiled class) and writes the
 * world into the {@code dstWorlds} section of the component's JSON in the catalog, together with the DST tests in the
 * package of the world that use it. The table of worlds in the camel-test-dst documentation is generated from the
 * catalog. Because the plugin reads the source, every value must be a string literal, a concatenation of string
 * literals with {@code +}, or an array of those; a constant fails the build.
 *
 * @since 4.24
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface DstWorld {

    /**
     * The scheme of the component whose consumer the world runs, for example {@code aws2-sqs}. In a module with several
     * components, this picks the JSON the world is written to; a scheme the module does not have fails the build.
     */
    String component();

    /** The fake of the external system: the class name of the fake, or how the client is faked if there is no class. */
    String fake();

    /** What the world exercises: the behaviours and options of the component, and the operations of the load. */
    String[] covers() default {};

    /** The faults the world injects (with the seed choosing when and how often). */
    String[] faults() default {};

    /** The invariants checked at the end of each run, named as in {@code dst.check(...)}. */
    String[] invariants() default {};

    /**
     * The JIRA issues of the world: the issue that added it, the bugs it is a regression test for and the bugs it
     * found, for example {@code CAMEL-25700}.
     */
    String[] issues() default {};
}
