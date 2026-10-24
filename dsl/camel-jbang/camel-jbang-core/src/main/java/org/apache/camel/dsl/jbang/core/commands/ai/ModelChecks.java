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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteNodes.Kind;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteNodes.Node;
import org.apache.camel.dsl.yaml.validator.EndpointRules;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * The Camel checks of a route source read into the model ({@link RouteModel}), whatever its DSL: the endpoint uris
 * against the catalog, the simple expressions, a to that needs toD, the direct and seda endpoints no route consumes.
 * They are the checks the YAML DSL has, run on the model instead of the YAML text (CAMEL-25208), and they report only
 * what they are sure of: a value the parser could not know is left alone.
 */
final class ModelChecks {

    /** The EIPs that consume from their endpoint; the others send to it. */
    private static final Set<String> CONSUMERS = EndpointRules.CONSUMERS;
    /** The one component whose path is a script, so what is in it is not the catalog's to say. */
    private static final Set<String> SCRIPT_PATH = Set.of("language");

    private final RouteModel model;
    private final List<Node> nodes;
    private final String[] lines;
    private final CamelCatalog catalog;
    private final List<String> errors = new ArrayList<>();

    private ModelChecks(RouteModel model, List<Node> nodes, String content, CamelCatalog catalog) {
        this.model = model;
        this.nodes = nodes;
        this.lines = content.split("\n", -1);
        this.catalog = catalog;
    }

    /**
     * The problems found, each "Line N: message".
     *
     * @param consumedElsewhere the direct: and seda: endpoints the other route files of the application consume; null
     *                          when they are not known, which leaves out the check of the endpoints no route consumes
     */
    static List<String> check(
            RouteModel model, List<Node> nodes, String content, CamelCatalog catalog, Set<String> consumedElsewhere) {
        ModelChecks checks = new ModelChecks(model, nodes, content, catalog);
        for (Node n : nodes) {
            if (n.kind() == Kind.ENDPOINT) {
                checks.endpoint(n);
            } else if (n.kind() == Kind.EXPRESSION) {
                checks.expression(n);
            }
        }
        checks.consumers(consumedElsewhere);
        return checks.errors;
    }

    /**
     * Only the direct: and seda: endpoints the routes send to and no route consumes, for an editor that marks them
     * apart from the other problems: as notes, which do not keep a file from being saved.
     *
     * @param consumedElsewhere the endpoints the other route files consume; null says nothing
     */
    static List<String> consumers(
            RouteModel model, List<Node> nodes, String content, CamelCatalog catalog, Set<String> consumedElsewhere) {
        ModelChecks checks = new ModelChecks(model, nodes, content, catalog);
        checks.consumers(consumedElsewhere);
        return checks.errors;
    }

    private void endpoint(Node n) {
        String uri = n.uri() != null ? n.uri().strip() : null;
        if (uri == null || uri.isEmpty() || RouteModel.hasUnknownValue(uri) || uri.startsWith("{{")) {
            return;
        }
        int lineIdx = Math.max(0, n.line() - 1);
        String prefix = EndpointChecks.linePrefix(lineIdx);
        if (EndpointChecks.SEVERAL_ENDPOINTS_PATTERN.matcher(uri).find()) {
            errors.add(prefix + "a " + n.eip() + " takes one endpoint; \"" + uri + "\" names several: send to each with a"
                       + " multicast (all of them), a recipientList (a list evaluated at runtime), or one "
                       + n.eip() + " per endpoint");
            return;
        }
        if (!uri.contains(":")) {
            uri = uri + ":";
        }
        String scheme = uri.substring(0, uri.indexOf(':'));
        boolean consumer = CONSUMERS.contains(n.eip());
        if ("from".equals(n.eip())) {
            ComponentModel cm = componentModel(scheme);
            if (cm != null && cm.isProducerOnly()) {
                errors.add(prefix + scheme + " is a producer-only component: a route cannot consume from it (the runtime"
                           + " says 'You cannot consume from this endpoint'); to pass messages between routes send to"
                           + " direct:name and consume from direct:name (or seda: for a queue)");
                return;
            }
        }
        if ("to".equals(n.eip())) {
            String expression = expressionInPath(uri, scheme);
            if (expression != null) {
                errors.add(prefix + "the uri of the to holds an expression (" + expression + ") but the endpoint of a to"
                           + " is fixed when the route starts, so it is sent as text: use toD to build the uri for"
                           + " each message");
                return;
            }
        }
        EndpointChecks.checkUri(errors, catalog, uri, uri, lineIdx, optionLines(uri, lineIdx), n.eip(), consumer,
                !consumer, true);
    }

    private void expression(Node n) {
        String text = n.text();
        if (!"simple".equals(n.language()) || text == null || text.isBlank() || RouteModel.hasUnknownValue(text)
                || SimpleChecks.hasPlaceholderAsLogicalOperand(text)) {
            return;
        }
        boolean logMessage = "log".equals(n.eip()) && "message".equals(n.option());
        SimpleChecks.checkText(errors, catalog, text, Math.max(1, n.line()), n.predicate() && !logMessage, logMessage,
                n.isInside("aggregate"));
    }

    /**
     * The direct: and seda: endpoints the routes send to and no route consumes: not the routes of this source, nor
     * those of the other route files. A direct: without a consumer fails the exchange, a seda: one fills a queue no one
     * reads. The endpoints and EIPs that count are the {@link EndpointRules} that {@code camel overview} uses too.
     */
    private void consumers(Set<String> consumedElsewhere) {
        if (consumedElsewhere == null) {
            return;
        }
        Set<String> own = EndpointConsumerChecks.consumed(model, nodes);
        if (own == null) {
            // a route that consumes an endpoint only known at runtime could consume any of them
            return;
        }
        Set<String> consumed = new HashSet<>(consumedElsewhere);
        consumed.addAll(own);
        List<Node> sends = new ArrayList<>();
        for (Node n : nodes) {
            if (n.kind() == Kind.ENDPOINT && EndpointRules.SENDERS.contains(n.eip())) {
                sends.add(n);
            }
        }
        sends.addAll(deadLetterEndpoints());
        sends.sort(Comparator.comparingInt(Node::line));
        Set<String> reported = new HashSet<>();
        for (Node n : sends) {
            if (RouteModel.hasUnknownValue(n.uri())) {
                continue;
            }
            String endpoint = EndpointRules.endpoint(n.uri());
            if (endpoint == null || consumed.contains(endpoint) || !reported.add(endpoint)) {
                continue;
            }
            String from = model.dsl() == RouteModel.Dsl.XML
                    ? "<from uri=\"" + endpoint + "\"/>" : "from(\"" + endpoint + "\")";
            errors.add(EndpointChecks.linePrefix(Math.max(0, n.line() - 1)) + "sends to " + endpoint
                       + ", and no route consumes it - not in this file, nor in the other route files of the directory: "
                       + EndpointRules.withoutConsumer(endpoint) + "; add a route with " + from + ", or correct the name");
        }
    }

    /**
     * The dead letter endpoints of the error handlers of the routes and route configurations, as endpoint nodes at the
     * line of their route: the error handler sends a failed message there.
     */
    private List<Node> deadLetterEndpoints() {
        List<Node> answer = new ArrayList<>();
        for (RouteDefinition r : model.routes()) {
            if (r.isErrorHandlerFactorySet()) {
                addDeadLetter(answer, r.getErrorHandlerFactory(), r.getLineNumber());
            }
        }
        for (RouteConfigurationDefinition rc : model.routeConfigurations()) {
            if (rc.getErrorHandler() != null) {
                addDeadLetter(answer, rc.getErrorHandler().getErrorHandlerType(), rc.getLineNumber());
            }
        }
        return answer;
    }

    private static void addDeadLetter(List<Node> answer, ErrorHandlerFactory factory, int line) {
        if (factory instanceof DeadLetterChannelDefinition dlc && dlc.getDeadLetterUri() != null) {
            answer.add(new Node(
                    Kind.ENDPOINT, Math.max(1, line), "deadLetterUri", dlc.getDeadLetterUri(), null, null,
                    null, false, List.of()));
        }
    }

    /**
     * The line of each option of the uri written on a line of its own, as a Java uri built over several lines: the
     * first line at or after the uri's that has the option.
     */
    private Map<String, Integer> optionLines(String uri, int lineIdx) {
        Map<String, Integer> answer = new LinkedHashMap<>();
        int q = uri.indexOf('?');
        if (q < 0) {
            return answer;
        }
        for (String pair : uri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String name = eq > 0 ? pair.substring(0, eq) : pair;
            if (name.isEmpty() || !name.matches("[\\w.\\[\\]-]+")) {
                continue;
            }
            for (int i = lineIdx; i < lines.length && i < lineIdx + 10; i++) {
                if (hasOption(lines[i], name)) {
                    if (i != lineIdx) {
                        answer.put(name, i);
                    }
                    break;
                }
            }
        }
        return answer;
    }

    /**
     * Whether the line has the option name= as an option of a uri: after ?, &, a quote, a space or a parenthesis.
     */
    private static boolean hasOption(String line, String name) {
        String key = name + "=";
        for (int i = line.indexOf(key); i >= 0; i = line.indexOf(key, i + 1)) {
            if (i > 0 && "?&\"' \t(".indexOf(line.charAt(i - 1)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first simple expression in the path of the uri (what comes before the options), or null when there is none or
     * the component evaluates its path for each message, as its catalog metadata says (micrometer:counter:${...}).
     */
    private String expressionInPath(String uri, String scheme) {
        if (SCRIPT_PATH.contains(scheme) || EndpointChecks.FILE_SCHEMES.contains(scheme)) {
            // the directory of a file endpoint has its own check, which says to move the dynamic part to fileName
            return null;
        }
        String head = uri.indexOf('?') > 0 ? uri.substring(0, uri.indexOf('?')) : uri;
        int start = head.indexOf("${");
        if (start < 0 || start >= 2 && head.startsWith(":#", start - 2)) {
            // :#${...} is a parameter the component binds per message, not a part of the address
            return null;
        }
        ComponentModel cm = componentModel(scheme);
        if (cm == null || cm.getEndpointPathOptions().stream().anyMatch(BaseOptionModel::isSupportSimpleExpression)) {
            // a component the catalog does not know: say nothing rather than the wrong thing
            return null;
        }
        int end = head.indexOf('}', start);
        return end > 0 ? head.substring(start, end + 1) : head.substring(start);
    }

    private ComponentModel componentModel(String scheme) {
        try {
            return catalog.componentModel(scheme);
        } catch (Exception e) {
            return null;
        }
    }
}
