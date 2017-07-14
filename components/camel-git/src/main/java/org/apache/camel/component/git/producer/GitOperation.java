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
package org.apache.camel.component.git.producer;

public interface GitOperation {

    String CLONE_OPERATION = "clone";
    String INIT_OPERATION = "init";
    String ADD_OPERATION = "add";
    String REMOVE_OPERATION = "remove";
    String COMMIT_OPERATION = "commit";
    String COMMIT_ALL_OPERATION = "commitAll";
    String CREATE_BRANCH_OPERATION = "createBranch";
    String DELETE_BRANCH_OPERATION = "deleteBranch";
    String CREATE_TAG_OPERATION = "createTag";
    String DELETE_TAG_OPERATION = "deleteTag";
    String STATUS_OPERATION = "status";
    String LOG_OPERATION = "log";
    String PUSH_OPERATION = "push";
    String PULL_OPERATION = "pull";
    String SHOW_BRANCHES = "showBranches";
    String CHERRYPICK_OPERATION = "cherryPick";
    String REMOTE_ADD_OPERATION = "remoteAdd";
    String REMOTE_LIST_OPERATION = "remoteList";
}
