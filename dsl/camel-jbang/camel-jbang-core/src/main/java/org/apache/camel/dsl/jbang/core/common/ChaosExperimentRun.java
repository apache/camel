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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonObject;

/**
 * One chaos experiment in one call (CAMEL-26786), as camel cmd chaos-experiment and the chaos_experiment tool run it:
 * it begins the experiment in the running exploration (its hypothesis and rules), sends the traffic of its feed, waits
 * until its expectations are decided, and ends it. The answer is what came of it (as the journal has it), with the
 * traffic that was sent and the time left of the exploration.
 * <p/>
 * The wait reads the report in the status of the integration, which is written every second. It ends as soon as an
 * expectation is unmet (FAIL) or all are met (PASS), when the exploration ended the experiment at its deadline, or
 * after the longest wait; an experiment ended then with an expectation not decided is INCONCLUSIVE. It is ended in any
 * case, also when the traffic could not be sent, so its rules do not stay.
 */
public final class ChaosExperimentRun {

    /** How long to wait for the verdict after the traffic, by default. */
    public static final long DEFAULT_WAIT = 2 * 60_000L;
    /** The longest wait for the verdict (the exploration ends the experiment at its deadline anyway). */
    public static final long MAX_WAIT = 10 * 60_000L;
    // the status of the integration is written every second
    private static final long POLL = 1000;

    private ChaosExperimentRun() {
    }

    /** What the experiment needs of the integration: the CLI and the tools each reach it their own way. */
    public interface Integration {

        /**
         * Sends the chaos action, and returns the answer.
         *
         * @throws RuntimeException when the integration refuses it, with the reason
         */
        JsonObject chaos(String command, Consumer<JsonObject> options);

        /** Sends the messages of the feed, and returns the summary of the send action. */
        JsonObject send(Feed feed);

        /** The status of the integration, as it wrote it last, or null. */
        JsonObject status();

        /**
         * Sends one HTTP request of a feed to the integration's own server (CAMEL-26793), and returns the status code
         * of its answer.
         */
        default int http(String method, String url, Map<String, String> headers, String body)
                throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
            headers.forEach(builder::header);
            builder.method(method, body == null || body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            return HttpFeedClient.INSTANCE.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    private static final class HttpFeedClient {
        static final HttpClient INSTANCE = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * The traffic of an experiment, as camel cmd send sends it.
     *
     * @param endpoint the endpoint or the id of the route to send to
     * @param body     the body, file:&lt;path&gt;, or file:&lt;folder&gt; for a message per file
     * @param headers  the headers as key=value lines, or null
     * @param repeat   how many messages in all; 0 to send once (each file of a folder once)
     * @param interval the pause between two messages, such as 200ms or a range such as 100..500, or null
     */
    public record Feed(String endpoint, String body, String headers, int repeat, String interval) {
    }

    /** The time, and a pause; the tests give a clock that does not wait. */
    interface Clock {
        long now();

        void sleep(long millis) throws InterruptedException;
    }

    private static final Clock SYSTEM = new Clock() {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    };

    /** The longest wait as text, such as 2m: 2 minutes when empty. */
    public static long waitMillis(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_WAIT;
        }
        long millis;
        try {
            millis = TimeUtils.toMilliSeconds(text.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The wait is not a time, such as 90s or 2m: " + text);
        }
        if (millis < 0 || millis > MAX_WAIT) {
            throw new IllegalArgumentException("The wait must be at most 10m, was: " + text);
        }
        return millis;
    }

    /**
     * Runs the experiment.
     *
     * @param  hypothesis what it tests, such as "a failed refund is read again" (optional)
     * @param  rules      its rules in their one-line form, separated by ;, one of them with an expectation
     * @param  feed       the traffic, or null when the routes feed themselves (a timer) or are fed otherwise
     * @param  wait       the longest wait for the verdict after the traffic, in millis
     * @param  note       what to note with it when it ends (optional)
     * @return            the experiment as it ended, with traffic, waited and timeLeft
     */
    public static JsonObject run(Integration integration, String hypothesis, String rules, Feed feed, long wait, String note) {
        try {
            return run(integration, hypothesis, rules, feed, wait, note, SYSTEM);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the verdict of the experiment", e);
        }
    }

    static JsonObject run(
            Integration integration, String hypothesis, String rules, Feed feed, long wait, String note, Clock clock)
            throws InterruptedException {
        // the status is read once for the checks of the feed
        JsonObject status = feed != null ? integration.status() : null;
        ChaosHttpFeed.Target http = null;
        if (feed != null) {
            // a feed that reaches no route makes an experiment without messages, whose verdict says nothing
            String why = checkFeed(status, feed.endpoint());
            if (why != null) {
                throw new IllegalArgumentException(why);
            }
            // a route that serves HTTP is fed with requests to the server of the integration
            List<Object> routes = status != null ? status.getCollection("routes") : null;
            JsonObject route = routes != null && !feed.endpoint().endsWith("*") ? route(routes, feed.endpoint()) : null;
            http = ChaosHttpFeed.target(status, route, feed.endpoint());
            // a file route reads the files its filter takes: the messages are written as such files
            String from = route != null ? route.getString("from") : null;
            String name = from != null && from.startsWith("file:") ? fileName(from, feed, clock.now()) : null;
            if (name != null) {
                feed = new Feed(
                        feed.endpoint(), feed.body(),
                        (feed.headers() != null ? feed.headers() + "\n" : "") + "CamelFileName=" + name, feed.repeat(),
                        feed.interval());
            }
        }
        // an expectation such as failed<=0 is decided when its within= is up: a shorter wait leaves it undecided
        long within = longestWithin(rules);
        if (within + POLL * 2 > wait) {
            wait = Math.min(within + POLL * 2, MAX_WAIT);
        }
        JsonObject begun = integration.chaos("experiment-begin", root -> {
            if (hypothesis != null && !hypothesis.isBlank()) {
                root.put("hypothesis", hypothesis);
            }
            root.put("rule", rules);
        });
        int number = begun.getIntegerOrDefault("number", 0);
        // a rule that matches nothing in the integration now (often a typo), and a feed to a real system
        List<Object> warnings = new ArrayList<>();
        List<Object> ruleWarnings = begun.getCollection("warnings");
        if (ruleWarnings != null) {
            warnings.addAll(ruleWarnings);
        }
        if (feed != null) {
            String real = feedWarning(status, feed.endpoint());
            if (real != null) {
                warnings.add(real);
            }
        }
        JsonObject traffic = null;
        long waited = 0;
        ChaosHttpFeed.Sender sender = null;
        Thread sending = null;
        try {
            if (http != null) {
                // sent while the experiment waits for its verdict, and stopped when it ends
                sender = new ChaosHttpFeed.Sender(integration, http, feed, clock);
                sending = Thread.ofVirtual().name("chaos-http-feed").start(sender);
            } else if (feed != null) {
                traffic = traffic(integration, feed);
            }
            long start = clock.now();
            while (true) {
                JsonObject seen = experiment(integration.status(), number);
                if (seen != null && (!seen.getBooleanOrDefault("open", true)
                        || !"PENDING".equals(seen.getString("verdict")))) {
                    break;
                }
                if (clock.now() - start >= wait) {
                    break;
                }
                clock.sleep(POLL);
            }
            waited = clock.now() - start;
        } finally {
            if (sender != null) {
                sender.stop();
                // the interrupt cancels a request in flight (HttpClient.send), and the sender sends no more after
                // stop: a request that does not end in time lingers at most until its timeout, also in a long-running
                // JVM such as the MCP server
                sending.interrupt();
                sending.join(5000);
                traffic = sender.toJson();
            }
            // ended whatever happened, so its rules do not stay
            JsonObject result = end(integration, number, note, clock);
            if (result != null) {
                begun = result;
            }
        }
        JsonObject answer = new JsonObject(begun);
        if (traffic != null) {
            answer.put("traffic", traffic);
        }
        answer.put("waited", waited);
        if (!warnings.isEmpty()) {
            answer.put("warnings", warnings);
        }
        JsonObject exploration = exploration(integration.status());
        if (exploration != null && exploration.containsKey("timeLeft")) {
            answer.put("timeLeft", exploration.getLong("timeLeft"));
        }
        return answer;
    }

    // the extension in an include filter (.*\.json, .*\.(csv|txt)) or an antInclude one (*.json)
    private static final Pattern INCLUDE_EXT = Pattern.compile("\\\\\\.\\(?([A-Za-z0-9]+)");
    private static final Pattern ANT_EXT = Pattern.compile("\\*\\.([A-Za-z0-9]+)");

    /**
     * The name of the files of a feed to a route that starts from file:<folder> (CAMEL-26795), so the route takes them:
     * chaos-<time>.<extension>, the extension from its include or antInclude filter, else from the body. The send
     * numbers it per message when it repeats. Null when the feed names its files itself, or the route reads one file
     * (fileName=), which the send writes as the endpoint has it.
     */
    static String fileName(String from, Feed feed, long now) {
        if (feed.headers() != null && feed.headers().toLowerCase(Locale.ROOT).contains("camelfilename")) {
            return null;
        }
        Map<String, Object> options;
        try {
            options = URISupport.parseQuery(URISupport.extractQuery(from));
        } catch (URISyntaxException e) {
            options = Map.of();
        }
        if (options.containsKey("fileName")) {
            return null;
        }
        String ext = null;
        if (options.get("include") instanceof String include) {
            Matcher m = INCLUDE_EXT.matcher(decode(include));
            ext = m.find() ? m.group(1) : null;
        }
        if (ext == null && options.get("antInclude") instanceof String ant) {
            Matcher m = ANT_EXT.matcher(decode(ant));
            ext = m.find() ? m.group(1) : null;
        }
        if (ext == null) {
            ext = extension(feed.body());
        }
        return "chaos-" + now + "." + ext;
    }

    // an option of the endpoint URI as the status has it, %5C for a backslash; a + stays a +, as in a regular expression
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    // the extension of a body: of the file it names, else json, xml or txt by its first character
    private static String extension(String body) {
        if (body == null) {
            return "txt";
        }
        if (body.startsWith("file:")) {
            String name = new File(body.substring(5)).getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "txt";
        }
        String b = body.stripLeading();
        return b.startsWith("{") || b.startsWith("[") ? "json" : b.startsWith("<") ? "xml" : "txt";
    }

    /** The longest within= of the rules, in millis, or 0 (an expectation without it has the time of its rule). */
    static long longestWithin(String rules) {
        long answer = 0;
        Matcher m = WITHIN.matcher(rules);
        while (m.find()) {
            try {
                answer = Math.max(answer, TimeUtils.toMilliSeconds(m.group(1)));
            } catch (RuntimeException e) {
                // not a time: camel-chaos refuses the rule with the reason
            }
        }
        return answer;
    }

    private static final Pattern WITHIN = Pattern.compile("(?:^|[\\s;])within=([^\\s;]+)");

    /** Sends the traffic: the summary of the send, or why it could not be sent. */
    private static JsonObject traffic(Integration integration, Feed feed) {
        JsonObject answer;
        try {
            answer = integration.send(feed);
        } catch (RuntimeException e) {
            answer = new JsonObject();
            answer.put("status", "failed");
            answer.put("error", e.getMessage());
        }
        if (answer == null) {
            answer = new JsonObject();
            answer.put("status", "failed");
        }
        if (!answer.containsKey("endpoint")) {
            answer.put("endpoint", feed.endpoint());
        }
        return answer;
    }

    /** Ends the experiment: what came of it, also when the exploration ended it at its deadline meanwhile. */
    private static JsonObject end(Integration integration, int number, String note, Clock clock)
            throws InterruptedException {
        try {
            return integration.chaos("experiment-end", root -> {
                if (note != null && !note.isBlank()) {
                    root.put("note", note);
                }
            });
        } catch (RuntimeException e) {
            // no experiment is open: the exploration ended it, which the next status says
            for (int i = 0; i < 3; i++) {
                JsonObject seen = experiment(integration.status(), number);
                if (seen != null && !seen.getBooleanOrDefault("open", true)) {
                    return seen;
                }
                clock.sleep(POLL);
            }
            throw e;
        }
    }

    /**
     * Why the feed cannot reach a route of the integration, or null when it does (or the status does not tell): the
     * feed is the id of a route, a pattern, or the endpoint a route starts from, as camel cmd send finds its target. It
     * is sent to that endpoint with a producer, which reaches the route for direct, seda, a stub or a broker, but not
     * for a route that makes its own messages (a timer), serves HTTP, or starts from a component that only consumes
     * (CAMEL-26792).
     */
    public static String checkFeed(JsonObject status, String feed) {
        List<Object> routes = status != null ? status.getCollection("routes") : null;
        if (routes == null || routes.isEmpty() || feed.endsWith("*") || ChaosHttpFeed.isPath(feed)) {
            return null;
        }
        JsonObject route = route(routes, feed);
        if (route != null) {
            return cannotFeed(route.getString("routeId"), route.getString("from"));
        }
        StringJoiner inputs = new StringJoiner(", ");
        for (Object o : routes) {
            if (o instanceof JsonObject r) {
                inputs.add(r.getString("routeId") + " (" + r.getString("from") + ")");
            }
        }
        return "The feed " + feed + " is not where a route starts, so no message would reach the integration: give the"
               + " id of a route, or the endpoint it starts from: " + inputs;
    }

    /**
     * A warning when the feed goes to a real remote system, as the route it reaches starts from one that is not stubbed
     * (a Kafka broker, a JMS queue), or null. Such a feed is fine for a local broker (camel infra run).
     */
    public static String feedWarning(JsonObject status, String feed) {
        List<Object> routes = status != null ? status.getCollection("routes") : null;
        JsonObject route = routes != null && !feed.endsWith("*") ? route(routes, feed) : null;
        String from = route != null ? route.getString("from") : null;
        ComponentModel model = from != null ? componentModel(from) : null;
        // a file is written to the folder the route polls, and an HTTP route is fed by the integration's own server
        if (model == null || !model.isRemote() || "file".equals(model.getScheme()) || ChaosHttpFeed.servedByCamel(from)) {
            return null;
        }
        String id = route.getString("routeId");
        Boolean stubbed = stubbed(status, id);
        if (stubbed == null || stubbed) {
            return null;
        }
        String scheme = model.getScheme();
        return "The feed goes to " + from + ", where route " + id + " starts, and it is not stubbed: the messages go to"
               + " the real " + scheme + ". Use a local one (camel infra run " + scheme + "), or run the integration"
               + " with --stub=" + scheme + " so no real system gets them";
    }

    /** The route the feed reaches: by its id, or by the endpoint it starts from; null when none. */
    private static JsonObject route(List<Object> routes, String feed) {
        String endpoint = normalize(feed);
        for (Object o : routes) {
            if (o instanceof JsonObject route) {
                String from = route.getString("from");
                if (feed.equals(route.getString("routeId"))
                        || from != null && (normalize(from).equals(endpoint) || normalize(from).startsWith(endpoint + "?"))) {
                    return route;
                }
            }
        }
        return null;
    }

    /** Why a message sent to the endpoint a route starts from does not reach it, or null when it does. */
    static String cannotFeed(String route, String from) {
        ComponentModel model = from != null ? componentModel(from) : null;
        if (model == null) {
            return null;
        }
        String label = model.getLabel() != null ? model.getLabel() : "";
        if (model.isConsumerOnly() && label.contains("scheduling")) {
            return "Route " + route + " starts from " + from + ", which makes its own messages: leave the feed out, as"
                   + " the traffic of the route comes by itself";
        }
        if (ChaosHttpFeed.servedByCamel(from)) {
            // fed with HTTP requests to the server of the integration (CAMEL-26793)
            return null;
        }
        if (label.contains("http")) {
            return "Route " + route + " starts from the HTTP endpoint " + from + ", which has its own address and an"
                   + " experiment cannot feed yet: feed a route that calls it, or leave the feed out when its traffic"
                   + " comes otherwise";
        }
        if (model.isConsumerOnly()) {
            return "Route " + route + " starts from " + from + ", which only consumes, so an experiment cannot send to"
                   + " it: feed another route, or leave the feed out when its traffic comes otherwise";
        }
        return null;
    }

    /** Whether the chaos report of the status has the route as stubbed; null when the report does not have it. */
    private static Boolean stubbed(JsonObject status, String route) {
        if (!(status.get("chaos") instanceof JsonObject chaos) || !(chaos.get("report") instanceof JsonObject report)) {
            return null;
        }
        List<Object> routes = report.getCollection("routes");
        if (routes != null) {
            for (Object o : routes) {
                if (o instanceof JsonObject r && route.equals(r.getString("route"))) {
                    return r.containsKey("stubbedConsumer");
                }
            }
        }
        return null;
    }

    private static ComponentModel componentModel(String uri) {
        int colon = uri.indexOf(':');
        return colon > 0 ? Catalog.INSTANCE.componentModel(uri.substring(0, colon)) : null;
    }

    private static final class Catalog {
        static final CamelCatalog INSTANCE = new DefaultCamelCatalog(true);
    }

    // kafka://refunds and kafka:refunds are the same endpoint
    private static String normalize(String uri) {
        return uri.replace("://", ":");
    }

    /** The experiment with the number in the report of the status, or null. */
    static JsonObject experiment(JsonObject status, int number) {
        JsonObject exploration = exploration(status);
        List<Object> experiments = exploration != null ? exploration.getCollection("experiments") : null;
        if (experiments == null) {
            return null;
        }
        for (Object o : experiments) {
            if (o instanceof JsonObject experiment && experiment.getIntegerOrDefault("number", -1) == number) {
                return experiment;
            }
        }
        return null;
    }

    /** The exploration in the report of the chaos console in the status, or null. */
    public static JsonObject exploration(JsonObject status) {
        if (status == null || !(status.get("chaos") instanceof JsonObject chaos)
                || !(chaos.get("report") instanceof JsonObject report)) {
            return null;
        }
        return report.get("exploration") instanceof JsonObject exploration ? exploration : null;
    }
}
