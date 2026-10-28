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
package org.apache.camel.component.github2.consumer;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * More than 100 commits pushed between two polls (a large merge, or the first poll after GitHub was unreachable for a
 * while): every one of them must be routed, not only the newest 100.
 */
class GitHub2CommitConsumerManyNewCommitsTest extends CamelTestSupport {

    private static final int NEW_COMMITS = 150;

    // newest first, as the API lists them; c0 is the commit the consumer starts after
    private volatile List<GHCommit> commits = createCommits(true);

    @BindToRegistry(GitHub2Constants.GITHUB_CLIENT)
    private final GitHub github = createGitHub();

    private final List<String> routed = new CopyOnWriteArrayList<>();
    // the number of commits read by each listing of the branch, one entry per listing
    private final List<AtomicInteger> listings = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("github2:commit/main?startingSha=c0&repoOwner=acme&repoName=demo&oauthToken=not-used"
                     + "&initialDelay=0&delay=10")
                        .routeId("commit").autoStartup(false)
                        .process(e -> routed.add(e.getMessage().getHeader(GitHub2Constants.GITHUB_COMMIT_SHA, String.class)));
            }
        };
    }

    @Test
    void everyNewCommitIsRouted() throws Exception {
        context.getRouteController().startRoute("commit");
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= NEW_COMMITS; i++) {
            expected.add("c" + i);
        }
        // the polls after the first one find no new commit; give them time to run
        await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expected, routed));
    }

    @Test
    void withoutTheLastCommitOnTheBranchOnlyTheNewest100AreRouted() throws Exception {
        // c0 is no longer on the branch (a force push): as before, only the 100 newest commits are considered
        commits = createCommits(false);
        context.getRouteController().startRoute("commit");

        List<String> expected = new ArrayList<>();
        for (int i = NEW_COMMITS - 99; i <= NEW_COMMITS; i++) {
            expected.add("c" + i);
        }
        await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expected, routed));
    }

    @Test
    void aBranchResetToRoutedCommitsIsNotListedAgainAtEveryPoll() throws Exception {
        context.getRouteController().startRoute("commit");
        await().atMost(10, TimeUnit.SECONDS).until(() -> routed.size() == NEW_COMMITS);

        // the branch is reset to c100: the last routed commit c150 is no longer on it, and the commits left on it were
        // all routed before
        commits = new ArrayList<>(commits.subList(NEW_COMMITS - 100, commits.size()));
        int listingsBefore = listings.size();

        // a poll that finds nothing new continues from the newest commit, so the next polls read one commit only, and
        // not the whole branch to look for c150 again
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(listings.size() >= listingsBefore + 3, "polls after the reset");
            assertEquals(1, listings.get(listings.size() - 2).get(), "commits read by a poll after the reset");
        });
        assertEquals(NEW_COMMITS, routed.size(), "commits routed");
    }

    private static List<GHCommit> createCommits(boolean withStartingCommit) {
        try {
            List<GHCommit> answer = new ArrayList<>();
            for (int i = NEW_COMMITS; i >= (withStartingCommit ? 0 : 1); i--) {
                answer.add(commit("c" + i));
            }
            return answer;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private GitHub createGitHub() {
        try {
            GHRepository repository = mock(GHRepository.class);
            GHCommitQueryBuilder commitQuery = mock(GHCommitQueryBuilder.class);
            when(commitQuery.from(anyString())).thenReturn(commitQuery);
            when(commitQuery.list()).thenAnswer(invocation -> paged(commits));
            when(repository.queryCommits()).thenReturn(commitQuery);

            GitHub client = mock(GitHub.class);
            when(client.getRepository("acme/demo")).thenReturn(repository);
            return client;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> PagedIterable<T> paged(List<T> items) {
        return new PagedIterable<>() {
            @Override
            public PagedIterator<T> _iterator(int pageSize) {
                Iterator<T> delegate = items.iterator();
                AtomicInteger read = new AtomicInteger();
                listings.add(read);
                PagedIterator<T> iterator = mock(PagedIterator.class);
                when(iterator.hasNext()).thenAnswer(invocation -> delegate.hasNext());
                when(iterator.next()).thenAnswer(invocation -> {
                    read.incrementAndGet();
                    return delegate.next();
                });
                return iterator;
            }
        };
    }

    private static GHCommit commit(String sha) throws IOException {
        GHCommit commit = mock(GHCommit.class, RETURNS_DEEP_STUBS);
        when(commit.getSHA1()).thenReturn(sha);
        when(commit.getAuthor()).thenReturn(null);
        when(commit.getCommitter()).thenReturn(null);
        when(commit.getHtmlUrl()).thenReturn(URI.create("https://github.invalid/acme/demo/commit/" + sha).toURL());
        when(commit.getCommitShortInfo().getMessage()).thenReturn("message of " + sha);
        return commit;
    }
}
