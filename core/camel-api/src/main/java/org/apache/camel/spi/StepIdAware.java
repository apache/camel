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
package org.apache.camel.spi;

import org.jspecify.annotations.Nullable;

/**
 * Marker for an object whose owning step id can be injected.
 * <p/>
 * This gives access to the step id at runtime, so a processor knows which step (the Step EIP) it is associated with.
 *
 * @see   IdAware
 * @see   RouteIdAware
 * @since 4.21
 */
public interface StepIdAware {

    /**
     * Gets the step id
     *
     * @since 4.21
     */
    @Nullable
    String getStepId();

    /**
     * Sets the step id
     *
     * @param stepId the step id
     * @since        4.21
     */
    void setStepId(String stepId);

}
