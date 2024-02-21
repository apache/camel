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
package org.apache.camel.tooling.model;

/**
 * A level of support for some Camel entity, such as component, language, data format, etc.
 */
public enum SupportLevel {

    /**
     * An experimental entity (not feature complete) that will change API, configuration, or functionality, or even be
     * removed in the future.
     *
     * Intended to be matured over time and become preview or stable.
     *
     * Using this entity is not recommended for production usage.
     */
    Experimental,

    /**
     * A preview entity that may change API, configuration, or functionality.
     *
     * Intended to be matured over time and become stable.
     *
     * Can be used in production but use with care.
     */
    Preview,

    /**
     * A stable entity.
     */
    Stable;

    public static final SupportLevel baseStability = Stable;

    public static SupportLevel safeValueOf(String level) {
        if (level == null) {
            return baseStability;
        }
        if (level.compareToIgnoreCase(Experimental.name()) == 0) {
            return Experimental;
        } else if (level.compareToIgnoreCase(Preview.name()) == 0) {
            return Preview;
        } else if (level.compareToIgnoreCase(Stable.name()) == 0) {
            return Stable;
        }
        throw new IllegalArgumentException("Unknown supportLevel: " + level);
    }

}
