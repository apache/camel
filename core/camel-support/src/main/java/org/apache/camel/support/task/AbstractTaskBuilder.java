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

package org.apache.camel.support.task;

/**
 * Provides common logic for task builders
 *
 * @param <T> the type of the task
 */
public abstract class AbstractTaskBuilder<T extends Task> implements TaskBuilder<T> {
    protected static final String DEFAULT_NAME = "camel-repeatable-task";
    private String name = AbstractTaskBuilder.DEFAULT_NAME;

    /**
     * Assigns a name to the task being built
     *
     * @param  name the name of the task
     * @return      A reference to this object
     */
    public AbstractTaskBuilder<T> withName(String name) {
        this.name = name;

        return this;
    }

    protected String getName() {
        return name;
    }
}
