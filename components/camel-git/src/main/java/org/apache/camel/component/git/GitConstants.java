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
    @Metadata(label = "producer", description = "The operation that the producer performs, used only when the operation option"
                                                + " is not set.",
              javaType = "String")
    String GIT_OPERATION = "CamelGitOperation";
    @Metadata(label = "producer",
              description = "The path of the file to stage (add) or to remove from the index and the working"
                            + " tree (remove), relative to the root of the working tree with / as separator."
                            + " A directory stands for all the files below it, and file globs are not"
                            + " supported. Required by add and remove.",
              javaType = "String")
    String GIT_FILE_NAME = "CamelGitFilename";
    @Metadata(label = "producer", description = "The message of the new commit in commit and commitAll. Required by both.",
              javaType = "String")
    String GIT_COMMIT_MESSAGE = "CamelGitCommitMessage";
    @Metadata(label = "producer", description = "The name of the author and committer of the new commit in commit and"
                                                + " commitAll. Used only together with CamelGitCommitEmail. Without both, JGit"
                                                + " takes them from user.name and user.email of the git config.",
              javaType = "String")
    String GIT_COMMIT_USERNAME = "CamelGitCommitUsername";
    @Metadata(label = "producer", description = "The email of the author and committer of the new commit in commit and"
                                                + " commitAll. Used only together with CamelGitCommitUsername.",
              javaType = "String")
    String GIT_COMMIT_EMAIL = "CamelGitCommitEmail";
    @Metadata(description = "The commit id (the sha): of the commit consumed by the commit consumer, of the commit to cherry"
                            + " pick in a cherryPick operation, and of the new commit after a commit or commitAll operation",
              javaType = "String")
    String GIT_COMMIT_ID = "CamelGitCommitId";
    @Metadata(label = "producer", description = "Whether commit and commitAll create a commit when there is nothing to commit."
                                                + " Replaces the allowEmpty option.",
              javaType = "Boolean")
    String GIT_ALLOW_EMPTY = "CamelGitAllowEmpty";
    @Metadata(label = "consumer", description = "The name of the author of the consumed commit.", javaType = "String")
    String GIT_COMMIT_AUTHOR_NAME = "CamelGitAuthorName";
    @Metadata(label = "consumer", description = "The name of the committer of the consumed commit.", javaType = "String")
    String GIT_COMMIT_COMMITTER_NAME = "CamelGitCommiterName";
    @Metadata(label = "consumer", description = "The commit time of the consumed commit, in seconds since"
                                                + " 1970-01-01T00:00:00Z.",
              javaType = "int")
    String GIT_COMMIT_TIME = "CamelGitCommitTime";
    @Metadata(label = "consumer", description = "The full name of the consumed ref, such as refs/heads/main,"
                                                + " refs/remotes/origin/main or refs/tags/v1. The branch and tag consumers set it"
                                                + " and also put it in the body.",
              javaType = "String")
    String GIT_BRANCH_NAME = "CamelGitBranchName";
    @Metadata(label = "consumer",
              description = "The full name of the ref that the consumed ref points to in the end, when it is"
                            + " a symbolic ref such as refs/remotes/origin/HEAD. For any other ref it is the"
                            + " same as CamelGitBranchName.",
              javaType = "String")
    String GIT_BRANCH_LEAF = "CamelGitBranchLeaf";
    @Metadata(label = "consumer", description = "The id of the object that the consumed ref points to: a commit for a branch or"
                                                + " a lightweight tag, a tag object for an annotated tag.",
              javaType = "String")
    String GIT_BRANCH_OBJECT_ID = "CamelGitBranchObjectId";
}
