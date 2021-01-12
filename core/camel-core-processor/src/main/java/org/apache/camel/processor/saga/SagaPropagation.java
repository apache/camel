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
package org.apache.camel.processor.saga;

/**
 * Enumerates all saga propagation modes.
 */
public enum SagaPropagation {

    /**
     * Join the existing saga or create a new one if it does not exist.
     */
    REQUIRED,

    /**
     * Always create a new saga. Suspend the old saga and resume it when the new one terminates.
     */
    REQUIRES_NEW,

    /**
     * A saga must be already present. The existing saga is joined.
     */
    MANDATORY,

    /**
     * If a saga already exists, then join it.
     */
    SUPPORTS,

    /**
     * If a saga already exists, it is suspended and resumed when the current block completes.
     */
    NOT_SUPPORTED,

    /**
     * The current block must never be invoked within a saga.
     */
    NEVER

}
