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
package org.apache.camel.component.git;

import org.apache.camel.spi.Metadata;

public interface GitConstants {
    @Metadata(label = "producer", description = "The operation to do on a repository, if not specified as endpoint option",
              javaType = "String")
    String GIT_OPERATION = "CamelGitOperation";
    @Metadata(label = "producer", description = "The file name in an add operation", javaType = "String")
    String GIT_FILE_NAME = "CamelGitFilename";
    @Metadata(label = "producer", description = "The commit message related in a commit operation", javaType = "String")
    String GIT_COMMIT_MESSAGE = "CamelGitCommitMessage";
    @Metadata(label = "producer", description = "The commit username in a commit operation", javaType = "String")
    String GIT_COMMIT_USERNAME = "CamelGitCommitUsername";
    @Metadata(label = "producer", description = "The commit email in a commit operation", javaType = "String")
    String GIT_COMMIT_EMAIL = "CamelGitCommitEmail";
    @Metadata(description = "The commit id", javaType = "String")
    String GIT_COMMIT_ID = "CamelGitCommitId";
    @Metadata(label = "producer", description = "The flag to manage empty git commits", javaType = "Boolean")
    String GIT_ALLOW_EMPTY = "CamelGitAllowEmpty";
    @Metadata(label = "consumer", description = "The author name", javaType = "String")
    String GIT_COMMIT_AUTHOR_NAME = "CamelGitAuthorName";
    @Metadata(label = "consumer", description = "The committer name", javaType = "String")
    String GIT_COMMIT_COMMITTER_NAME = "CamelGitCommiterName";
    @Metadata(label = "consumer", description = "The commit time", javaType = "int")
    String GIT_COMMIT_TIME = "CamelGitCommitTime";
    @Metadata(label = "consumer", description = "The leaf", javaType = "String")
    String GIT_BRANCH_LEAF = "CamelGitBranchLeaf";
    @Metadata(label = "consumer", description = "The object id", javaType = "String")
    String GIT_BRANCH_OBJECT_ID = "CamelGitBranchObjectId";
    String GIT_TAG_LEAF = "CamelGitTagLeaf";
    String GIT_TAG_OBJECT_ID = "CamelGitTagObjectId";
}
