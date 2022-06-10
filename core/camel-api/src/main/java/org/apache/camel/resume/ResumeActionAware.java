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
 * Provides an interface for adapters and other resume-related code to allow them to offer a way to set actions to be
 * executed during the resume process. This is most likely to be used in situations where the resume adapter does not
 * have the information required to resume because the resume logic is too broad (i.e.: a database component trying to
 * resume operations cannot know in advance what is the SQL to be executed).
 *
 * This provides a way for integrations to inject that part of the logic into the resume API.
 */
public interface ResumeActionAware extends ResumeAdapter {

    /**
     * Sets an action that will be executed during resume
     *
     * @param resumeAction the action to execute during resume
     */
    void setResumeAction(ResumeAction resumeAction);
}
