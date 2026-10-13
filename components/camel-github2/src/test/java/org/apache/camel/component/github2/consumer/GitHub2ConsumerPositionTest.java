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
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Message;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventInfo;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitCommit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The github2 consumers must not move their position (last event id, last pull request, last comment id, seen tags,
 * seen commits) past what they did not hand to the route: when a route is suspended while a poll is being routed, the
 * rest of that poll has to be routed after the resume. A commit whose author or committer cannot be looked up is routed
 * all the same, with the names that git recorded in the commit, and neither it nor the other commits of the poll are
 * lost.
 */
class GitHub2ConsumerPositionTest extends CamelTestSupport {

    private static final String NOT_SET = "(not set)";
    private static final String OPTIONS = "repoOwner=acme&repoName=demo&oauthToken=not-used&initialDelay=0&delay=10";

    // what the (mocked) repository returns; replaced as a whole, so that a poll sees all the new items at once
    private volatile List<GHEventInfo> events = List.of();
    private volatile List<GHPullRequest> pullRequests = List.of();
    private volatile List<GHTag> tags = List.of();
    private volatile List<GHCommit> commits = List.of();
    private final AtomicInteger polls = new AtomicInteger();

    @BindToRegistry(GitHub2Constants.GITHUB_CLIENT)
    private final GitHub github = createGitHub();

    // counts the polls that ended with an exception
    @BindToRegistry("countingPollStrategy")
    private final CountingPollStrategy pollStrategy = new CountingPollStrategy();

    private final List<String> routed = new CopyOnWriteArrayList<>();
    // the author and committer headers of the routed commits, by sha
    private final Map<String, String> authorHeaders = new ConcurrentHashMap<>();
    private final Map<String, String> committerHeaders = new ConcurrentHashMap<>();
    // the item whose processor suspends its route (once)
    private volatile String suspendAt;
    private final AtomicBoolean suspendRequested = new AtomicBoolean();
    private final AtomicReference<Exception> suspendFailure = new AtomicReference<>();
    private volatile Thread suspender;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("github2:event?" + OPTIONS).routeId("event").autoStartup(false)
                        .process(e -> route("event", String.valueOf(
                                e.getMessage().getHeader(GitHub2Constants.GITHUB_EVENT_PAYLOAD, GHEventInfo.class).getId())));
                from("github2:pullRequest?" + OPTIONS).routeId("pullRequest").autoStartup(false)
                        .process(e -> route("pullRequest",
                                String.valueOf(e.getMessage().getHeader(GitHub2Constants.GITHUB_PULLREQUEST))));
                from("github2:pullRequestComment?" + OPTIONS).routeId("pullRequestComment").autoStartup(false)
                        .process(e -> route("pullRequestComment",
                                String.valueOf(e.getMessage().getBody(GHIssueComment.class).getId())));
                from("github2:tag?" + OPTIONS).routeId("tag").autoStartup(false)
                        .process(e -> route("tag", e.getMessage().getBody(GHTag.class).getName()));
                from("github2:commit/main?startingSha=beginning&pollStrategy=#countingPollStrategy&" + OPTIONS)
                        .routeId("commit").autoStartup(false)
                        .process(e -> {
                            Message message = e.getMessage();
                            String sha = message.getHeader(GitHub2Constants.GITHUB_COMMIT_SHA, String.class);
                            authorHeaders.put(sha, headerOrNotSet(message, GitHub2Constants.GITHUB_COMMIT_AUTHOR));
                            committerHeaders.put(sha, headerOrNotSet(message, GitHub2Constants.GITHUB_COMMIT_COMMITTER));
                            route("commit", sha);
                        });
            }
        };
    }

    @Test
    void eventsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        context.getRouteController().startRoute("event");
        suspendAt = "2";
        // the API returns the most recent event first
        events = List.of(event(4), event(3), event(2), event(1));

        suspendedDuringThePollAndResumed("event", List.of("1", "2"), List.of("1", "2", "3", "4"));
    }

    @Test
    void pullRequestsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        context.getRouteController().startRoute("pullRequest");
        suspendAt = "2";
        // the API returns the most recent pull request first
        pullRequests = List.of(pullRequest(4), pullRequest(3), pullRequest(2), pullRequest(1));

        suspendedDuringThePollAndResumed("pullRequest", List.of("1", "2"), List.of("1", "2", "3", "4"));
    }

    @Test
    void commentsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        context.getRouteController().startRoute("pullRequestComment");
        suspendAt = "12";
        pullRequests = List.of(pullRequest(2, 12, 14), pullRequest(1, 11, 13));

        suspendedDuringThePollAndResumed("pullRequestComment", List.of("11", "12"), List.of("11", "12", "13", "14"));
    }

    @Test
    void tagsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        context.getRouteController().startRoute("tag");
        suspendAt = "v2";
        // the API returns the most recent tag first
        tags = List.of(tag("v4"), tag("v3"), tag("v2"), tag("v1"));

        suspendedDuringThePollAndResumed("tag", List.of("v1", "v2"), List.of("v1", "v2", "v3", "v4"));
    }

    @Test
    void commitsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        context.getRouteController().startRoute("commit");
        suspendAt = "c2";
        // the API returns the most recent commit first
        commits = List.of(commit("c4"), commit("c3"), commit("c2"), commit("c1"));

        suspendedDuringThePollAndResumed("commit", List.of("c1", "c2"), List.of("c1", "c2", "c3", "c4"));
    }

    @Test
    void aCommitWhoseAuthorLookupFailsOnceIsRoutedWithTheNameFromTheCommit() throws Exception {
        GHCommit first = commit("c1", "Git Author 1", "Git Committer 1");
        GHUser author1 = user("Profile Author 1");
        GHUser committer1 = user("Profile Committer 1");
        when(first.getAuthor()).thenReturn(author1);
        when(first.getCommitter()).thenReturn(committer1);
        // the lookup of the author fails once (it would work in a later poll), the one of the committer works
        GHCommit failsOnce = commit("c2", "Git Author 2", "Git Committer 2");
        GHUser author2 = user("Profile Author 2");
        GHUser committer2 = user("Profile Committer 2");
        AtomicInteger lookups = new AtomicInteger();
        when(failsOnce.getAuthor()).thenAnswer(invocation -> {
            if (lookups.incrementAndGet() == 1) {
                throw new IOException("Simulated: 502 Bad Gateway");
            }
            return author2;
        });
        when(failsOnce.getCommitter()).thenReturn(committer2);

        context.getRouteController().startRoute("commit");
        commits = List.of(commit("c3"), failsOnce, first);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("c1", "c2", "c3"), routed, "the commit and the ones that follow it are routed"));
        assertEquals("Git Author 2", authorHeaders.get("c2"), "the name from the commit, as the lookup failed");
        assertEquals("Profile Committer 2", committerHeaders.get("c2"), "the name of the user that was looked up");
        assertEquals("Profile Author 1", authorHeaders.get("c1"));
        assertEquals("Profile Committer 1", committerHeaders.get("c1"));
        assertNothingElseIsRouted(List.of("c1", "c2", "c3"));
        assertEquals(1, lookups.get(), "the routed commit is not looked up again");
        assertEquals(0, pollStrategy.failedPolls.get());
    }

    @Test
    void aCommitWhoseUserLookupsAlwaysFailIsRoutedOnceAndDoesNotFailThePolls() throws Exception {
        // for example the account was deleted: every lookup is answered with a 404. git recorded no committer name
        GHCommit alwaysFails = commit("c2", "Git Author 2", null);
        when(alwaysFails.getAuthor()).thenThrow(new GHFileNotFoundException("Simulated: 404 Not Found"));
        when(alwaysFails.getCommitter()).thenThrow(new GHFileNotFoundException("Simulated: 404 Not Found"));

        context.getRouteController().startRoute("commit");
        commits = List.of(commit("c3"), alwaysFails, commit("c1"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("c1", "c2", "c3"), routed, "the commit and the ones that follow it are routed"));
        assertEquals("Git Author 2", authorHeaders.get("c2"), "the name from the commit, as the lookup failed");
        assertEquals(NOT_SET, committerHeaders.get("c2"), "no lookup and no name in the commit: the header is not set");
        assertNothingElseIsRouted(List.of("c1", "c2", "c3"));
        // routed by the first poll and not read again by the following ones
        verify(alwaysFails, times(1)).getAuthor();
        verify(alwaysFails, times(1)).getCommitter();
        assertEquals(0, pollStrategy.failedPolls.get(), "the failed lookups do not fail a poll");
    }

    @Test
    void commitsOfAPollWithAFailedAuthorLookupAreNotLost() throws Exception {
        // on main the poll ended with this exception after it had remembered c1, c2 and c3, and none was ever routed
        GHCommit failsOnce = commit("c2");
        AtomicBoolean failed = new AtomicBoolean();
        when(failsOnce.getAuthor()).thenAnswer(invocation -> {
            if (failed.compareAndSet(false, true)) {
                throw new IOException("Simulated: connection reset");
            }
            return null;
        });

        context.getRouteController().startRoute("commit");
        commits = List.of(commit("c3"), failsOnce, commit("c1"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("c1", "c2", "c3"), routed, "the commits of the poll with the failed lookup"));
        assertTrue(failed.get());
        assertNothingElseIsRouted(List.of("c1", "c2", "c3"));
    }

    private void suspendedDuringThePollAndResumed(String routeId, List<String> beforeTheSuspend, List<String> all)
            throws Exception {
        awaitRouteSuspended(routeId);
        assertEquals(beforeTheSuspend, routed, "the suspend ends the batch after the exchange that is being routed");

        context.getRouteController().resumeRoute(routeId);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(all, routed, "routed after the resume"));
        assertNothingElseIsRouted(all);
    }

    private void assertNothingElseIsRouted(List<String> expected) {
        // a few more polls: nothing is routed twice
        int before = polls.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> polls.get() >= before + 3);
        assertEquals(expected, routed);
    }

    private void route(String routeId, String item) {
        if (item.equals(suspendAt)) {
            suspendRouteDuringThePoll(routeId);
        }
        routed.add(item);
    }

    /**
     * Called by the thread that polls: suspends the route from another thread, as a route must not suspend itself, and
     * returns when the suspend has reached the consumer (it no longer routes the following exchanges of the batch).
     */
    private void suspendRouteDuringThePoll(String routeId) {
        if (!suspendRequested.compareAndSet(false, true)) {
            return;
        }
        ScheduledBatchPollingConsumer consumer = (ScheduledBatchPollingConsumer) context.getRoute(routeId).getConsumer();
        suspender = new Thread(() -> {
            try {
                context.getRouteController().suspendRoute(routeId);
            } catch (Exception e) {
                suspendFailure.set(e);
            }
        }, "suspend-route");
        suspender.start();

        CountDownLatch batchEnded = new CountDownLatch(1);
        Thread watcher = new Thread(() -> {
            await().atMost(20, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
            batchEnded.countDown();
        }, "watch-consumer");
        watcher.start();

        // an interrupt of the thread that polls must not end this wait
        boolean interrupted = false;
        boolean ended = false;
        long remaining = TimeUnit.SECONDS.toNanos(30);
        long deadline = System.nanoTime() + remaining;
        while (!ended && remaining > 0) {
            try {
                ended = batchEnded.await(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            remaining = deadline - System.nanoTime();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitRouteSuspended(String routeId) throws InterruptedException {
        await().atMost(20, TimeUnit.SECONDS).until(() -> suspender != null);
        suspender.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(suspender.isAlive(), "the route suspend has returned");
        assertNull(suspendFailure.get());
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus(routeId));
    }

    private GitHub createGitHub() {
        try {
            GHRepository repository = mock(GHRepository.class);
            when(repository.listEvents()).thenAnswer(invocation -> paged(() -> events));
            when(repository.getPullRequests(GHIssueState.OPEN)).thenAnswer(invocation -> {
                polls.incrementAndGet();
                return new ArrayList<>(pullRequests);
            });
            when(repository.listTags()).thenAnswer(invocation -> paged(() -> tags));
            GHCommitQueryBuilder commitQuery = mock(GHCommitQueryBuilder.class);
            when(commitQuery.from(anyString())).thenReturn(commitQuery);
            when(commitQuery.list()).thenAnswer(invocation -> paged(() -> commits));
            when(repository.queryCommits()).thenReturn(commitQuery);

            GitHub client = mock(GitHub.class);
            when(client.getRepository("acme/demo")).thenReturn(repository);
            return client;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A page iterable over the current content of a list. Every iteration counts as a poll.
     */
    @SuppressWarnings("unchecked")
    private <T> PagedIterable<T> paged(Supplier<List<T>> items) {
        return new PagedIterable<>() {
            @Override
            public PagedIterator<T> _iterator(int pageSize) {
                polls.incrementAndGet();
                Iterator<T> delegate = items.get().iterator();
                PagedIterator<T> iterator = mock(PagedIterator.class);
                when(iterator.hasNext()).thenAnswer(invocation -> delegate.hasNext());
                when(iterator.next()).thenAnswer(invocation -> delegate.next());
                return iterator;
            }
        };
    }

    private static GHEventInfo event(long id) {
        GHEventInfo event = mock(GHEventInfo.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(GHEvent.PUSH);
        return event;
    }

    private static GHPullRequest pullRequest(int number, long... commentIds) throws IOException {
        GHPullRequest pullRequest = mock(GHPullRequest.class);
        when(pullRequest.getNumber()).thenReturn(number);
        List<GHIssueComment> comments = new ArrayList<>();
        for (long id : commentIds) {
            // a real comment: getId has generated bridge methods, which a mock does not stub reliably
            GHIssueComment comment = new GHIssueComment();
            setField(GHObject.class, comment, "id", id);
            setField(GHIssueComment.class, comment, "owner", pullRequest);
            comments.add(comment);
        }
        when(pullRequest.getComments()).thenReturn(comments);
        return pullRequest;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GHTag tag(String name) {
        GHTag tag = mock(GHTag.class);
        when(tag.getName()).thenReturn(name);
        return tag;
    }

    private static String headerOrNotSet(Message message, String name) {
        String value = message.getHeader(name, String.class);
        return value != null ? value : NOT_SET;
    }

    private static GHUser user(String name) throws IOException {
        GHUser user = mock(GHUser.class);
        when(user.getName()).thenReturn(name);
        return user;
    }

    /**
     * A commit as the list of commits returns it: with the names that git recorded for the author and the committer (a
     * real object, as its getters have generated bridge methods, which a mock does not stub reliably).
     */
    private static GHCommit commit(String sha, String gitAuthor, String gitCommitter) throws IOException {
        GHCommit.ShortInfo info = new GHCommit.ShortInfo();
        setField(GitCommit.class, info, "message", "message of " + sha);
        setField(GitCommit.class, info, "author", gitUser(gitAuthor));
        setField(GitCommit.class, info, "committer", gitUser(gitCommitter));
        GHCommit commit = mock(GHCommit.class);
        when(commit.getSHA1()).thenReturn(sha);
        when(commit.getHtmlUrl()).thenReturn(URI.create("https://github.invalid/acme/demo/commit/" + sha).toURL());
        when(commit.getCommitShortInfo()).thenReturn(info);
        return commit;
    }

    private static GitUser gitUser(String name) {
        GitUser user = new GitUser();
        setField(GitUser.class, user, "name", name);
        return user;
    }

    private static final class CountingPollStrategy extends DefaultPollingConsumerPollStrategy {
        private final AtomicInteger failedPolls = new AtomicInteger();

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception {
            failedPolls.incrementAndGet();
            return super.rollback(consumer, endpoint, retryCounter, cause);
        }
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
