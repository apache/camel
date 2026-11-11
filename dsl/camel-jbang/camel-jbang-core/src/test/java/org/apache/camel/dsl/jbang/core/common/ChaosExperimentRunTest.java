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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One chaos experiment in one call (CAMEL-26786): begin, traffic, the wait for the verdict in the status, and the end,
 * against a fake integration and a clock that does not wait.
 */
class ChaosExperimentRunTest {

    /** The integration: what it was asked, and the status it shows, one per read (the last one stays). */
    private static final class FakeIntegration implements ChaosExperimentRun.Integration {
        final List<JsonObject> actions = new ArrayList<>();
        final Deque<JsonObject> statuses = new ArrayDeque<>();
        ChaosExperimentRun.Feed sent;
        RuntimeException sendFails;
        RuntimeException endFails;
        RuntimeException beginFails;
        List<String> beginWarnings;
        JsonObject ended = experiment("FAIL", false);
        // the HTTP requests of a feed, and the reads of the status that wait until they are all sent
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch httpDone;
        int statusReads;

        @Override
        public JsonObject chaos(String command, Consumer<JsonObject> options) {
            JsonObject action = new JsonObject();
            action.put("command", command);
            options.accept(action);
            actions.add(action);
            if ("experiment-begin".equals(command)) {
                if (beginFails != null) {
                    throw beginFails;
                }
                JsonObject begun = experiment("PENDING", true);
                if (beginWarnings != null) {
                    begun.put("warnings", new JsonArray(beginWarnings));
                }
                return begun;
            }
            if (endFails != null) {
                throw endFails;
            }
            return ended;
        }

        @Override
        public JsonObject send(ChaosExperimentRun.Feed feed) {
            sent = feed;
            if (sendFails != null) {
                throw sendFails;
            }
            return new JsonObject(Map.of("status", "success", "endpoint", "direct://refunds", "sent", 20L, "failed", 0L));
        }

        @Override
        public JsonObject status() {
            // the first read is the check of the feed, before the requests
            if (httpDone != null && statusReads++ > 0) {
                try {
                    httpDone.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return statuses.size() > 1 ? statuses.poll() : statuses.peek();
        }

        @Override
        public int http(String method, String url, Map<String, String> headers, String body) {
            requests.add(method + " " + url + " " + headers + " " + body);
            int code = requests.size() % 4 == 0 ? 503 : 200;
            if (httpDone != null) {
                httpDone.countDown();
            }
            return code;
        }

        void shows(String verdict, boolean open) {
            statuses.add(statusWith(experiment(verdict, open)));
        }
    }

    /** A clock that moves on when it is asked to sleep. */
    private static final class FakeClock implements ChaosExperimentRun.Clock {
        long now = 1_000_000;
        int sleeps;

        @Override
        public long now() {
            return now;
        }

        @Override
        public void sleep(long millis) {
            sleeps++;
            now += millis;
        }
    }

    private static JsonObject experiment(String verdict, boolean open) {
        return new JsonObject(Map.of("number", 1, "verdict", verdict, "open", open, "rules", new JsonArray(List.of("r1"))));
    }

    private static JsonObject statusWith(JsonObject experiment) {
        JsonObject exploration = new JsonObject(
                Map.of("running", true, "timeLeft", 300_000L, "experiments",
                        new JsonArray(List.of(experiment))));
        JsonObject report = new JsonObject(Map.of("exploration", exploration));
        return new JsonObject(Map.of("chaos", new JsonObject(Map.of("enabled", true, "report", report))));
    }

    private final FakeIntegration integration = new FakeIntegration();
    private final FakeClock clock = new FakeClock();

    private static final String RULE = "route=refunds error rate=every:5 expect=refunds:failed<=0 within=30s";
    private static final ChaosExperimentRun.Feed FEED
            = new ChaosExperimentRun.Feed("refunds", "file:/feed/", null, 20, "200ms");

    @Test
    void theTrafficThenTheWaitUntilTheVerdict() throws Exception {
        // read first by the check of the feed
        integration.shows("PENDING", true);
        integration.shows("PENDING", true);
        integration.shows("PENDING", true);
        integration.shows("FAIL", true);

        JsonObject answer = ChaosExperimentRun.run(integration, "a failed refund is read again", RULE, FEED, 60_000,
                "kafka commits a failed record", clock);

        assertThat(integration.actions).extracting(a -> a.getString("command"))
                .containsExactly("experiment-begin", "experiment-end");
        assertThat(integration.actions.get(0).getString("hypothesis")).isEqualTo("a failed refund is read again");
        assertThat(integration.actions.get(0).getString("rule")).isEqualTo(RULE);
        assertThat(integration.actions.get(1).getString("note")).isEqualTo("kafka commits a failed record");
        assertThat(integration.sent).isEqualTo(FEED);
        // two reads that were pending, a second apart
        assertThat(clock.sleeps).isEqualTo(2);
        assertThat(answer.getString("verdict")).isEqualTo("FAIL");
        assertThat(answer.getLong("waited")).isEqualTo(2_000);
        assertThat(answer.getLong("timeLeft")).isEqualTo(300_000);
        assertThat(((JsonObject) answer.get("traffic")).getLong("sent")).isEqualTo(20);
    }

    @Test
    void theWaitIsUpBeforeTheVerdict() throws Exception {
        integration.shows("PENDING", true);
        integration.ended = experiment("INCONCLUSIVE", false);

        // an expectation without within= has the time of its rule, which the wait does not know
        JsonObject answer = ChaosExperimentRun.run(integration, null, "route=refunds error expect=refunds:failed<=0", null,
                5_000, null, clock);

        assertThat(answer.getString("verdict")).isEqualTo("INCONCLUSIVE");
        assertThat(answer.getLong("waited")).isEqualTo(5_000);
        assertThat(answer).doesNotContainKey("traffic");
        assertThat(integration.actions.get(0)).doesNotContainKey("hypothesis");
        assertThat(integration.actions.get(1)).doesNotContainKey("note");
    }

    @Test
    void endedWhenTheTrafficCannotBeSent() throws Exception {
        integration.shows("PENDING", true);
        integration.sendFails = new IllegalArgumentException("Header must be in key=value format, was: x");
        integration.ended = experiment("INCONCLUSIVE", false);

        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, FEED, 2_000, null, clock);

        JsonObject traffic = (JsonObject) answer.get("traffic");
        assertThat(traffic.getString("status")).isEqualTo("failed");
        assertThat(traffic.getString("error")).isEqualTo("Header must be in key=value format, was: x");
        assertThat(traffic.getString("endpoint")).isEqualTo("refunds");
        assertThat(integration.actions).extracting(a -> a.getString("command")).contains("experiment-end");
    }

    @Test
    void theExplorationEndedItAtItsDeadline() throws Exception {
        integration.shows("PENDING", true);
        // ended by the exploration: the end is refused, and the status has what came of it
        integration.shows("FAIL", false);
        integration.endFails = new IllegalStateException("No experiment is open");

        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, null, 60_000, null, clock);

        assertThat(answer.getString("verdict")).isEqualTo("FAIL");
        assertThat(answer.getBoolean("open")).isFalse();
    }

    @Test
    void aRefusedExperimentIsNotEnded() {
        integration.beginFails = new IllegalStateException("Experiment 1 is open: end it first");

        assertThatThrownBy(() -> ChaosExperimentRun.run(integration, null, RULE, FEED, 60_000, null, clock))
                .hasMessage("Experiment 1 is open: end it first");
        assertThat(integration.actions).hasSize(1);
        assertThat(integration.sent).isNull();
    }

    @Test
    void aFeedThatReachesNoRouteIsRefusedBeforeTheExperiment() {
        JsonObject status = new JsonObject(
                Map.of("routes", new JsonArray(
                        List.of(
                                new JsonObject(Map.of("routeId", "orders", "from", "timer://orders?period=1000")),
                                new JsonObject(
                                        Map.of("routeId", "refunds", "from", "kafka://refunds?brokers=localhost:9092"))))));
        integration.statuses.add(status);

        // the route id, the endpoint it starts from (with or without //), and a pattern reach a route
        assertThat(ChaosExperimentRun.checkFeed(status, "refunds")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "kafka:refunds")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "kafka://refunds")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "ref*")).isNull();
        // an endpoint no route starts from: no message would arrive, so the verdict would say nothing
        assertThatThrownBy(() -> ChaosExperimentRun.run(integration, null, RULE,
                new ChaosExperimentRun.Feed("direct:refunds", "R-1", null, 5, null), 60_000, null, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The feed direct:refunds is not where a route starts, so no message would reach the"
                            + " integration: give the id of a route, or the endpoint it starts from:"
                            + " orders (timer://orders?period=1000), refunds (kafka://refunds?brokers=localhost:9092)");
        assertThat(integration.actions).isEmpty();
    }

    @Test
    void aRouteThatCannotBeFedSaysWhy() {
        JsonObject status = new JsonObject(
                Map.of("routes", new JsonArray(
                        List.of(
                                new JsonObject(Map.of("routeId", "orders", "from", "timer://orders?period=1000")),
                                new JsonObject(Map.of("routeId", "payment", "from", "platform-http:///payment")),
                                new JsonObject(Map.of("routeId", "api", "from", "rest:post:/refunds")),
                                new JsonObject(Map.of("routeId", "leader", "from", "master:lock:direct:start")),
                                new JsonObject(Map.of("routeId", "hooks", "from", "netty-http:http://0.0.0.0:9000/hooks")),
                                new JsonObject(Map.of("routeId", "remote", "from", "undertow:http://shop.example.com:9000/x")),
                                new JsonObject(Map.of("routeId", "inbox", "from", "file://inbox?delete=true")),
                                new JsonObject(Map.of("routeId", "billing", "from", "seda://billing"))))));
        integration.statuses.add(status);

        // a route that makes its own messages (CAMEL-26792)
        assertThat(ChaosExperimentRun.checkFeed(status, "orders")).isEqualTo(
                "Route orders starts from timer://orders?period=1000, which makes its own messages: leave the feed out,"
                                                                             + " as the traffic of the route comes by itself");
        // a route served by the HTTP server of Camel is fed with requests to it (CAMEL-26793), another HTTP server not
        assertThat(ChaosExperimentRun.checkFeed(status, "payment")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "platform-http:/payment")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "api")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "POST /refunds")).isNull();
        // an HTTP server of its own on this machine is fed at its address; one on another host is not
        assertThat(ChaosExperimentRun.checkFeed(status, "hooks")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "remote"))
                .startsWith("Route remote starts from the HTTP endpoint undertow:http://shop.example.com:9000/x, which an"
                            + " experiment cannot feed");
        // a component that only consumes
        assertThat(ChaosExperimentRun.checkFeed(status, "leader")).contains("which only consumes");
        // a file is written to the folder the route polls, and seda is in memory
        assertThat(ChaosExperimentRun.checkFeed(status, "inbox")).isNull();
        assertThat(ChaosExperimentRun.checkFeed(status, "billing")).isNull();

        // refused before the experiment begins
        assertThatThrownBy(() -> ChaosExperimentRun.run(integration, null, RULE,
                new ChaosExperimentRun.Feed("orders", "O-1", null, 5, null), 60_000, null, clock))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("which makes its own messages");
        assertThat(integration.actions).isEmpty();
    }

    private static JsonObject served(JsonObject status) {
        JsonObject served = new JsonObject(status);
        served.put("routes", new JsonArray(
                List.of(
                        new JsonObject(
                                Map.of("routeId", "payment", "from", "platform-http:///payment?httpMethodRestrict=POST")),
                        new JsonObject(Map.of("routeId", "orders", "from", "rest:get:/orders/{id}")))));
        served.put("platform-http", new JsonObject(
                Map.of(
                        "server", "http://0.0.0.0:8080",
                        "endpoints", new JsonArray(
                                List.of(
                                        new JsonObject(
                                                Map.of("url", "http://0.0.0.0:8080/api/payment", "path", "/api/payment",
                                                        "verbs", "POST")))))));
        served.put("rests", new JsonObject(
                Map.of("rests", new JsonArray(
                        List.of(
                                new JsonObject(
                                        Map.of("url", "http://0.0.0.0:8080/api/orders/{id}", "method", "get", "routeId",
                                                "orders")))))));
        return served;
    }

    @Test
    void anHttpRouteIsFedWithRequestsToTheServerOfTheIntegration() throws Exception {
        // read by the check of the feed: the routes and the HTTP server
        integration.statuses.add(served(statusWith(experiment("PENDING", true))));
        integration.shows("PENDING", true);
        integration.shows("FAIL", true);
        integration.httpDone = new CountDownLatch(4);

        JsonObject answer = ChaosExperimentRun.run(integration, "a client gets a 503, not a 500", RULE,
                new ChaosExperimentRun.Feed("payment", "{\"amount\": 5}", "X-Shop=demo", 4, null), 60_000, null, clock);

        // the path as served (with its context path), the method of the route, the headers and a JSON content type
        assertThat(integration.requests).hasSize(4).allMatch(r -> r.equals(
                "POST http://localhost:8080/api/payment {X-Shop=demo, Content-Type=application/json} {\"amount\": 5}"));
        assertThat(integration.sent).isNull();
        JsonObject traffic = (JsonObject) answer.get("traffic");
        assertThat(traffic.getString("endpoint")).isEqualTo("POST http://localhost:8080/api/payment");
        assertThat(traffic.getLong("sent")).isEqualTo(4);
        assertThat(((JsonObject) traffic.get("statusCodes")).getLong("200")).isEqualTo(3);
        assertThat(((JsonObject) traffic.get("statusCodes")).getLong("503")).isEqualTo(1);
        assertThat(answer.getString("verdict")).isEqualTo("FAIL");
    }

    @Test
    void whereTheRequestsOfAnHttpFeedGo() {
        JsonObject status = served(new JsonObject());
        List<Object> routes = status.getCollection("routes");

        // a path to call, with or without a method (CAMEL-26793)
        assertThat(ChaosHttpFeed.target(status, null, "PUT /api/orders/42"))
                .hasToString("PUT http://localhost:8080/api/orders/42");
        assertThat(ChaosHttpFeed.target(status, null, "/api/payment")).hasToString("POST http://localhost:8080/api/payment");
        // a route with a parameter in its path: the feed gives the path to call
        assertThatThrownBy(() -> ChaosHttpFeed.target(status, (JsonObject) routes.get(1), "orders"))
                .hasMessage("Route orders serves /api/orders/{id}, with a parameter: give the feed as the path to call,"
                            + " such as POST /api/orders/1");
        // an HTTP server of its own: its address, called as localhost, with its method
        assertThat(ChaosHttpFeed.target(status,
                new JsonObject(
                        Map.of("routeId", "hooks",
                                "from", "netty-http://http://0.0.0.0:9000/hooks?httpMethodRestrict=PUT")),
                "hooks")).hasToString("PUT http://localhost:9000/hooks");
        assertThat(ChaosHttpFeed.ownAddress("jetty:http://localhost:8181")).isEqualTo("http://localhost:8181/");
        assertThat(ChaosHttpFeed.ownAddress("undertow:https://10.0.0.5:8443/api")).isNull();
        // another route: not for HTTP
        assertThat(ChaosHttpFeed.target(status, new JsonObject(Map.of("routeId", "x", "from", "seda://x")), "x")).isNull();
        // a server whose port the status does not have
        assertThatThrownBy(() -> ChaosHttpFeed.target(new JsonObject(Map.of("routes", status.get("routes"))),
                (JsonObject) routes.get(0), "payment"))
                .hasMessageContaining("the port of the HTTP server of the integration is not known");
    }

    @Test
    void theFilesOfAFileFeedPassTheFilterOfTheRoute() {
        ChaosExperimentRun.Feed json = new ChaosExperimentRun.Feed("invoices", "{\"n\": 1}", null, 5, null);
        // the extension of the include or antInclude filter (CAMEL-26795)
        assertThat(ChaosExperimentRun.fileName("file://invoices?delete=true&include=.*%5C.json", json, 42))
                .isEqualTo("chaos-42.json");
        assertThat(ChaosExperimentRun.fileName("file://in?include=%28%3Fi%29.*%5C.%28csv%7Ctxt%29", json, 42))
                .isEqualTo("chaos-42.csv");
        assertThat(ChaosExperimentRun.fileName("file://in?antInclude=*.xml", json, 42)).isEqualTo("chaos-42.xml");
        assertThat(ChaosExperimentRun.fileName("file://invoices?include=.*%5C.json",
                new ChaosExperimentRun.Feed("invoices", "plain text", null, 0, null), 42)).isEqualTo("chaos-42.json");
        // no filter: by the body, or the file it names
        assertThat(ChaosExperimentRun.fileName("file://in", json, 42)).isEqualTo("chaos-42.json");
        assertThat(ChaosExperimentRun.fileName("file://in",
                new ChaosExperimentRun.Feed("in", "<order/>", null, 0, null), 42)).isEqualTo("chaos-42.xml");
        assertThat(ChaosExperimentRun.fileName("file://in",
                new ChaosExperimentRun.Feed("in", "file:/feed/order.csv", null, 0, null), 42)).isEqualTo("chaos-42.csv");
        // the feed names its files, or the route reads one file
        assertThat(ChaosExperimentRun.fileName("file://in",
                new ChaosExperimentRun.Feed("in", "x", "CamelFileName=mine.txt", 0, null), 42)).isNull();
        assertThat(ChaosExperimentRun.fileName("file://in?fileName=orders.csv", json, 42)).isNull();
    }

    @Test
    void aFileRouteIsFedWithNamedFiles() throws Exception {
        JsonObject status = statusWith(experiment("PENDING", true));
        status.put("routes", new JsonArray(
                List.of(
                        new JsonObject(Map.of("routeId", "invoices", "from", "file://invoices?include=.*%5C.json")))));
        integration.statuses.add(status);
        integration.shows("FAIL", true);

        ChaosExperimentRun.run(integration, null, RULE,
                new ChaosExperimentRun.Feed("invoices", "{}", "country=DK", 5, null), 60_000, null, clock);

        assertThat(integration.sent.headers()).isEqualTo("country=DK\nCamelFileName=chaos-1000000.json");
    }

    @Test
    void aReplayPutsBackTheFilesTheRouteTookAfterTheRulesAreAdded() throws Exception {
        JsonObject status = statusWith(experiment("PENDING", true));
        status.put("routes", new JsonArray(
                List.of(new JsonObject(Map.of("routeId", "invoices", "from", "file://invoices")))));
        integration.statuses.add(status);
        integration.shows("PENDING", true);
        integration.shows("PASS", true);

        JsonObject result = ChaosExperimentRun.run(integration, null, RULE, ChaosExperimentRun.Feed.replay("invoices"),
                60_000, null, clock);

        // nothing is sent: the files keep their names, so the route takes the same files each time
        assertThat(integration.sent).isNull();
        // again while the experiment runs, so a rule with a rate meets enough messages
        assertThat(integration.actions).extracting(a -> a.getString("command"))
                .containsSubsequence("experiment-begin", "replay", "replay", "experiment-end");
        assertThat(integration.actions).filteredOn(a -> "replay".equals(a.getString("command")))
                .extracting(a -> a.getString("route")).containsOnly("invoices");
        JsonObject traffic = (JsonObject) result.get("traffic");
        assertThat(traffic.getString("status")).isEqualTo("success");
        assertThat(traffic.getString("endpoint")).isEqualTo("invoices");
    }

    @Test
    void theRouteTheFeedReachesGoesWithTheExperiment() throws Exception {
        JsonObject status = statusWith(experiment("PENDING", true));
        status.put("routes", new JsonArray(
                List.of(new JsonObject(Map.of("routeId", "refunds", "from", "direct://refunds")))));
        integration.statuses.add(status);
        integration.shows("FAIL", true);

        // by the endpoint it starts from: camel-chaos refuses the experiment when it leads to no route of its
        // expectations (CAMEL-26803)
        ChaosExperimentRun.run(integration, null, RULE,
                new ChaosExperimentRun.Feed("direct:refunds", "{}", null, 1, null), 60_000, null, clock);

        assertThat(integration.actions.get(0).getString("command")).isEqualTo("experiment-begin");
        assertThat(integration.actions.get(0).getString("feedRoute")).isEqualTo("refunds");
    }

    @Test
    void aFeedToARealSystemIsWarned() {
        JsonObject report = new JsonObject(
                Map.of("routes", new JsonArray(
                        List.of(
                                new JsonObject(Map.of("route", "refunds")),
                                new JsonObject(Map.of("route", "returns", "stubbedConsumer", "kafka")),
                                new JsonObject(Map.of("route", "inbox"))))));
        JsonObject status = new JsonObject(
                Map.of(
                        "routes", new JsonArray(
                                List.of(
                                        new JsonObject(
                                                Map.of("routeId", "refunds", "from", "kafka://refunds?brokers=localhost:9092")),
                                        new JsonObject(Map.of("routeId", "returns", "from", "kafka://returns")),
                                        new JsonObject(Map.of("routeId", "inbox", "from", "file://inbox")))),
                        "chaos", new JsonObject(Map.of("report", report))));

        // not stubbed: the messages go to the broker (CAMEL-26792)
        assertThat(ChaosExperimentRun.feedWarning(status, "refunds", List.of())).isEqualTo(
                "The feed goes to kafka://refunds?brokers=localhost:9092, where route refunds starts, and it is not"
                                                                                           + " stubbed: the messages go to the real kafka. Use a local one (camel infra"
                                                                                           + " run kafka), or run the integration with --stub=kafka so no real system"
                                                                                           + " gets them");
        // stubbed, a file, or a report that does not tell: no warning
        assertThat(ChaosExperimentRun.feedWarning(status, "returns", List.of())).isNull();
        assertThat(ChaosExperimentRun.feedWarning(status, "inbox", List.of())).isNull();
        assertThat(ChaosExperimentRun.feedWarning(new JsonObject(Map.of("routes", status.get("routes"))), "refunds", List.of()))
                .isNull();
    }

    @Test
    void aFeedToABrokerOfCamelInfraIsNotWarned() {
        JsonObject report
                = new JsonObject(Map.of("routes", new JsonArray(List.of(new JsonObject(Map.of("route", "refunds"))))));
        // the brokers of Kafka come from the services of the status, as they are often configured on the component
        JsonObject services = new JsonObject(
                Map.of("services", new JsonArray(
                        List.of(new JsonObject(
                                Map.of("component", "kafka", "direction", "in", "routeId", "refunds", "serviceUrl",
                                        "localhost:32775"))))));
        JsonObject status = new JsonObject(
                Map.of(
                        "routes",
                        new JsonArray(List.of(new JsonObject(Map.of("routeId", "refunds", "from", "kafka://refunds")))),
                        "services", services, "chaos", new JsonObject(Map.of("report", report))));
        List<InfraServices.Service> infra = List.of(new InfraServices.Service("kafka", 42, Set.of(32775)));

        // camel infra runs Kafka there: a local broker, not a real system (CAMEL-26804)
        assertThat(ChaosExperimentRun.feedWarning(status, "refunds", infra)).isNull();
        assertThat(ChaosExperimentRun.localInfra(status, "refunds", infra).name()).isEqualTo("kafka");
        // camel infra runs Kafka on another port, or nothing: the broker may be a real one
        List<InfraServices.Service> other = List.of(new InfraServices.Service("kafka", 42, Set.of(9092)));
        assertThat(ChaosExperimentRun.feedWarning(status, "refunds", other)).startsWith("The feed goes to kafka://refunds");
        assertThat(ChaosExperimentRun.feedWarning(status, "refunds", List.of())).startsWith("The feed goes to kafka://refunds");
    }

    @Test
    void aRuleThatMatchesNothingComesWithItsWarning() throws Exception {
        integration.shows("PENDING", true);
        integration.beginWarnings = List.of("r1: endpoint=file:/parked matches no endpoint of the context now");
        integration.ended = experiment("PASS", false);

        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, null, 1_000, null, clock);

        // the PASS says nothing: the AI reads why
        assertThat(answer.<List<Object>> getCollection("warnings"))
                .containsExactly("r1: endpoint=file:/parked matches no endpoint of the context now");
    }

    @Test
    void theWaitIsAtLeastTheWithinOfTheExpectations() throws Exception {
        integration.shows("PENDING", true);
        integration.ended = experiment("PASS", false);

        // RULE has within=30s: a wait of 5s would end it before failed<=0 is decided
        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, null, 5_000, null, clock);

        assertThat(answer.getLong("waited")).isEqualTo(32_000);
        assertThat(ChaosExperimentRun.longestWithin("route=a error expect=a:1 within=1m; route=b drop expect=b:1 within=90s"))
                .isEqualTo(90_000);
        // the ; between the rules is not part of the time, also when it follows the time without a space
        assertThat(ChaosExperimentRun.longestWithin("route=a error expect=a:1 within=3m; route=b drop expect=b:1 within=90s"))
                .isEqualTo(180_000);
        assertThat(ChaosExperimentRun.longestWithin("route=a error expect=a:1 within=3m;route=b drop within=1m"))
                .isEqualTo(180_000);
        assertThat(ChaosExperimentRun.longestWithin("route=a error")).isZero();
    }

    @Test
    void theWait() {
        assertThat(ChaosExperimentRun.waitMillis(null)).isEqualTo(ChaosExperimentRun.DEFAULT_WAIT);
        assertThat(ChaosExperimentRun.waitMillis("90s")).isEqualTo(90_000);
        assertThatThrownBy(() -> ChaosExperimentRun.waitMillis("11m")).hasMessageContaining("at most 10m");
        assertThatThrownBy(() -> ChaosExperimentRun.waitMillis("soon")).hasMessageContaining("not a time");
    }
}
