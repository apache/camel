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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Static semantic expert capabilities, available without constructing providers or loading models.
 *
 * @since 4.23
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Target(ElementType.TYPE)
public @interface SemanticExpert {
    enum InputType {
        TEXT,
        STRUCTURED
    }

    enum ResultType {
        BOOLEAN,
        CHOICE,
        SCORE
    }

    enum Instructions {
        REQUIRED,
        OPTIONAL,
        UNSUPPORTED
    }

    String name();

    String description();

    String provider();

    String artifactId();

    InputType[] inputTypes();

    ResultType[] resultTypes();

    Instructions instructions();

    boolean callerDefinedCriteria();

    boolean booleanProbability() default false;

    boolean choiceProbabilities() default false;

    ResultType[] confidenceTypes() default {};

    String probabilityMeaning() default "";

    String confidenceMeaning() default "";

    String trueMeaning() default "";

    /** Zero means no statically known limit. */
    int maxChoices() default 0;

    /** Zero means no statically known limit. */
    int maxScoreLevels() default 0;
}
