/**
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
package org.apache.camel.component.git;

public interface GitOperation {

    public final static String CLONE_OPERATION = "clone";
    public final static String INIT_OPERATION = "init";
    public final static String ADD_OPERATION = "add";
    public final static String REMOVE_OPERATION = "remove";
    public final static String COMMIT_OPERATION = "commit";
    public final static String COMMIT_ALL_OPERATION = "commitAll";
    public final static String CREATE_BRANCH_OPERATION = "createBranch";
    public final static String DELETE_BRANCH_OPERATION = "deleteBranch";
    public final static String STATUS_OPERATION = "status";
    public final static String LOG_OPERATION = "log";
    public final static String PUSH_OPERATION = "push";
    public final static String PULL_OPERATION = "pull";
}
