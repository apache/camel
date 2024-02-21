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

package org.apache.camel.resume;

/**
 * An interface to represent an object which wishes to support resume operations using a {@link ResumeStrategy}.
 */
public interface ResumeAware<T extends ResumeStrategy> {

    /**
     * Injects the {@link ResumeStrategy} instance into the object
     *
     * @param resumeStrategy the resume strategy
     */
    void setResumeStrategy(T resumeStrategy);

    /**
     * Gets the {@link ResumeStrategy} instance
     *
     * @return the resume strategy
     */
    T getResumeStrategy();

    /**
     * Allows the implementation to provide custom adapter factories. It binds the service name provided in the
     * {@link org.apache.camel.spi.annotations.JdkService} annotation in the adapter with the resume aware class. This
     * allows the adapter to be resolved automatically in runtime while also allowing fallback to reusable adapters when
     * available.
     *
     * @return
     */
    default String adapterFactoryService() {
        return ResumeAdapter.RESUME_ADAPTER_FACTORY;
    }
}
