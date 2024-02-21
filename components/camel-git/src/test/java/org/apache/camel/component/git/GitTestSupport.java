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
import java.io.IOException;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitTestSupport extends CamelTestSupport {

    public final String gitLocalRepo = "testRepo";

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

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        File localPath = File.createTempFile(gitLocalRepo, "");
        localPath.delete();
        File path = new File(gitLocalRepo);
        path.deleteOnExit();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        File path = new File(gitLocalRepo);
        deleteDirectory(path);
    }

    protected Repository getTestRepository()
            throws IOException, IllegalStateException, GitAPIException, ConfigInvalidException {
        File gitRepo = new File(gitLocalRepo, ".git");
        SystemReader.getInstance().getUserConfig().clear(); //clears user config in JGit context, that way there are no environmental contamination that may affect the tests
        Git.init().setDirectory(new File(gitLocalRepo, "")).setInitialBranch("master").setBare(false).call();
        // now open the resulting repository with a FileRepositoryBuilder
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = builder.setGitDir(gitRepo).readEnvironment() // scan
                // environment
                // GIT_*
                // variables
                .findGitDir() // scan up the file system tree
                .build();
        return repo;
    }

    protected Git getGitTestRepository() throws IOException, IllegalStateException, GitAPIException, ConfigInvalidException {
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
