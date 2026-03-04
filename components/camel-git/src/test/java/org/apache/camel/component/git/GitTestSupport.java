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

import java.io.File;
import java.util.UUID;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.SystemReader;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitTestSupport extends CamelTestSupport {

    private final File localRepo = new File("target/test-repo-" + UUID.randomUUID());
    private Repository repository;

    private final File cloneRepo = new File("target/clone-repo-" + UUID.randomUUID());

    public final String filenameToAdd = "filetest.txt";

    public final String filenameBranchToAdd = "filetest1.txt";

    public final String commitMessage = "Test commit";

    public final String commitMessageAll = "Test commit all";

    public final String commitMessageBranch = "Test commit on a branch";

    public final String commitMessageMergeBranch = "Test merge on a target branch";

    public final String branchTest = "testBranch";

    public final String targetBranchTest = "targetTestBranch";

    public final String tagTest = "testTag";

    public final String remoteUriTest = "https://github.com/oscerd/json-webserver-example.git";

    @Override
    public void doPostTearDown() {
        deleteDirectory(localRepo);
        deleteDirectory(cloneRepo);
    }

    public File getGitDir() {
        return localRepo;
    }

    public File getCloneDir() {
        return cloneRepo;
    }

    protected Repository getTestRepository() throws Exception {
        if (repository == null) {
            File gitRepo = new File(localRepo, ".git");
            SystemReader.getInstance().getUserConfig().clear(); //clears user config in JGit context, that way there are no environmental contamination that may affect the tests
            Git.init().setDirectory(localRepo).setInitialBranch("master").setBare(false).call();
            // now open the resulting repository with a FileRepositoryBuilder
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repo = builder.setGitDir(gitRepo).readEnvironment() // scan
                    // environment
                    // GIT_*
                    // variables
                    .findGitDir() // scan up the file system tree
                    .build();
            repository = repo;
        }
        return repository;
    }

    protected Git getGitTestRepository() throws Exception {
        return new Git(getTestRepository());
    }

    protected void validateGitLogs(Git git, String... messages) throws GitAPIException {
        Iterable<RevCommit> logs = git.log().call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(messages[count], rev.getShortMessage());
            count++;
        }
        assertEquals(messages.length, count);
    }

}
