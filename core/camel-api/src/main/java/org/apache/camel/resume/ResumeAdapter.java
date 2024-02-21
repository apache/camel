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
 * A resume adapter provides the component-specific logic that plugs the more generic strategic with the lower level
 * requirements of the component being used.
 *
 * The adapter class responsibility is to bind the component-specific part of the logic to the more generic handling of
 * the resume strategy. The adapter is always component specific and some components may have more than one.
 *
 * It is the responsibility of the supported components to implement the custom implementation for this part of the
 * resume API, as well as to offer component-specific interfaces that can be specialized by other integrations.
 */
public interface ResumeAdapter {
    String RESUME_ADAPTER_FACTORY = "adapter-factory";

    /**
     * Execute the resume logic for the adapter
     */
    void resume();
}
