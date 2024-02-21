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
package org.apache.camel;

/**
 * Recording state of steps during startup to capture execution time, and being able to emit events to diagnostic tools
 * such as Java Flight Recorder.
 */
public interface StartupStep {

    /**
     * The source class type of the step
     */
    String getType();

    /**
     * Name of the step
     */
    String getName();

    /**
     * Description of the step
     */
    String getDescription();

    /**
     * The id of the step
     */
    int getId();

    /**
     * The id of the parent step
     */
    int getParentId();

    /**
     * The step level (sub step of previous steps)
     */
    int getLevel();

    /**
     * Ends the step.
     */
    void endStep();

    /**
     * Gets the begin time (optional).
     */
    long getBeginTime();

    /**
     * Gets the duration the step took (optional)
     */
    long getDuration();

}
