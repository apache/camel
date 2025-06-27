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
package org.apache.camel.component.git.consumer;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitConsumerCommitTest extends GitTestSupport {

    @Test
    public void commitConsumerTest() throws Exception {
        // Init
        MockEndpoint mockResultCommit = getMockEndpoint("mock:result-commit");
        mockResultCommit.expectedMessageCount(2);

        Git git = getGitTestRepository();
        File gitDir = new File(getGitDir(), ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(getGitDir(), filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        File fileToAdd1 = new File(getGitDir(), filenameBranchToAdd);
        fileToAdd1.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();
        status = git.status().call();
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        git.commit().setMessage("Test test Commit").call();
        validateGitLogs(git, "Test test Commit", commitMessage);
        // Test
        mockResultCommit.assertIsSatisfied();

        // Check
        Exchange ex1 = mockResultCommit.getExchanges().get(0);
        Exchange ex2 = mockResultCommit.getExchanges().get(1);
        assertEquals(commitMessage, ex2.getMessage().getBody(String.class));
        assertEquals("Test test Commit", ex1.getMessage().getBody(String.class));
        git.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // force create git repo before routes
        getTestRepository();
        final String dir = getGitDir().getPath();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("git://" + dir + "?type=commit&branchName=master")
                        .to("log:all?showAll=true")
                        .to("mock:result-commit");
            }
        };
    }

}
