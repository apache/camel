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
package org.apache.camel.semantic;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;

/** An expert-owned operation, including the meaning of its input, parameters and result. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface SemanticOperation {
    /** Stable operation name used by declarations. */
    String name();

    /** Human-readable purpose of the operation. */
    String description();

    /** Supported shapes of the selected message state. */
    InputType[] inputTypes();

    /** Required content and structure within the supported input shapes. */
    String inputRequirements();

    /** Application parameters accepted by this operation. */
    SemanticParameter[] parameters() default {};

    /** Shape of the decision returned by the expert. */
    ResultType resultType();

    /** Meaning of the decision, including the positive class for a boolean result. */
    String resultMeaning();

    /** Fixed allowed categories or labels; empty leaves the vocabulary to the expert. */
    String[] labels() default {};

    /**
     * Name of a List&lt;String&gt; parameter supplying ordered score level descriptions. When supplied with N levels,
     * the score ranges from 0 to N-1 and may be fractional. List index i describes score i; per-level probabilities,
     * when supported, use the decimal index string as their key ("0", "1", ...). Empty declares no such relationship.
     * Only valid for SCORE operations. The expert validates evaluation-specific bounds and probability keys.
     */
    String scoreLevelsParameter() default "";

    /** Inclusive minimum for a score result. */
    double minimum() default Double.NEGATIVE_INFINITY;

    /** Inclusive maximum for a score result. */
    double maximum() default Double.POSITIVE_INFINITY;

    /** Whether results may carry one probability in addition to their decision. */
    boolean probability() default false;

    /** Whether results may carry probabilities keyed by category or label. */
    boolean probabilities() default false;

    /** Meaning of the probability or per-label probabilities, when supported. */
    String probabilityMeaning() default "";

    /** Whether results may carry a separate confidence value. */
    boolean confidence() default false;

    /** Meaning of the confidence value, when supported. */
    String confidenceMeaning() default "";
}
