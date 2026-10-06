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
package org.apache.camel.component.openfeature;

import org.apache.camel.spi.Metadata;

public final class OpenFeatureConstants {

    @Metadata(description = "Overrides the configured flag key for this message.", javaType = "String",
              applicableFor = "openfeature")
    public static final String FLAG_KEY = "CamelOpenFeatureFlagKey";

    @Metadata(description = "Sets the targeting key for the OpenFeature evaluation context.", javaType = "String",
              applicableFor = "openfeature")
    public static final String TARGETING_KEY = "CamelOpenFeatureTargetingKey";

    @Metadata(description = "A Map<String, Object> of additional evaluation context key-value pairs.",
              javaType = "java.util.Map", applicableFor = "openfeature")
    public static final String EVALUATION_CONTEXT = "CamelOpenFeatureEvaluationContext";

    @Metadata(description = "Sets the evaluation type (boolean, variant) for the OpenFeature evaluation.",
              javaType = "String", applicableFor = "openfeature")
    public static final String EVALUATION_TYPE = "CamelOpenFeatureEvaluationType";

    private OpenFeatureConstants() {
    }
}
