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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * A remote file endpoint (sftp, ftp, ftps) without a username logs in as anonymous. r26 to r28 connect-sftp-transfer (5
 * runs): the from: of the route downloaded the batches with {{sftp.username}} and {{sftp.password}}, and the to: that
 * uploaded a receipt, or a failed batch, to the same server had no login: "Cannot connect to
 * sftp://anonymous@localhost:2222", Auth fail, and the write had been accepted.
 * <p/>
 * Only reported when another endpoint of the same server (the same scheme, host and port, with the placeholders of the
 * project's .properties files resolved) logs in, in the file or in another YAML route file of the project: the message
 * gives the login options of that endpoint to copy, in the form the endpoint is written in.
 */
final class RemoteFileLogins {

    private static final Set<String> SCHEMES = Set.of("sftp", "ftp", "ftps");
    /** The keys whose value is an endpoint: a uri, or a mapping with uri: and parameters:. */
    private static final Set<String> ENDPOINT_KEYS = Set.of("from", "to", "toD", "to-d", "wireTap", "wire-tap",
            "enrich", "pollEnrich", "poll-enrich", "poll");
    /** The options that log in: an endpoint with one of them is not anonymous. */
    private static final Set<String> LOGIN_OPTIONS = Set.of("username", "password", "privateKey", "privateKeyFile",
            "privateKeyUri", "keyPair", "account");
    /** The login options of the components when the catalog cannot say (its security group). */
    private static final Set<String> SECURITY_OPTIONS = Set.of("username", "password", "account", "privateKey",
            "privateKeyFile", "privateKeyUri", "privateKeyPassphrase", "keyPair", "knownHosts", "knownHostsFile",
            "knownHostsUri", "useUserKnownHostsFile", "strictHostKeyChecking");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}:]+)(?::([^}]*))?}}");

    private RemoteFileLogins() {
    }

    /**
     * A remote file endpoint as written: its line, the EIP, the server, its options (path and query options and the
     * parameters, by name, as written) and how its options are written (under parameters:, in the uri, or neither).
     */
    private record Endpoint(
            int line, String eip, String uri, String scheme, String host, String port, boolean userInfo,
            Map<String, String> options, boolean parameters, boolean query) {

        boolean logsIn() {
            return userInfo || options.keySet().stream().anyMatch(LOGIN_OPTIONS::contains);
        }

        boolean hasUsername() {
            return userInfo || options.containsKey("username");
        }
    }

    /**
     * The endpoints without a login whose server another endpoint of the file or project logs in to, each "Line N:
     * message".
     *
     * @param content   the YAML route
     * @param catalog   for the login options of the component (its security group)
     * @param directory the project directory, for its .properties files and other route files; may be null
     * @param fileName  the file of the content, left out of the other route files
     */
    static List<String> validate(String content, CamelCatalog catalog, Path directory, String fileName) {
        List<String> answer = new ArrayList<>();
        if (content == null || !content.contains("ftp")) {
            return answer;
        }
        List<Endpoint> endpoints = endpoints(content);
        if (endpoints.stream().allMatch(Endpoint::logsIn)) {
            return answer;
        }
        Map<String, String> properties = directory != null ? properties(directory) : Map.of();
        // the endpoints of the other route files of the project, by file, as places to copy a login from
        Map<String, List<Endpoint>> others = new LinkedHashMap<>();
        others.put(null, endpoints);
        if (directory != null) {
            others.putAll(otherFiles(directory, fileName));
        }
        try {
            for (Endpoint e : endpoints) {
                String server = e.logsIn() ? null : server(e, properties);
                if (server == null) {
                    continue;
                }
                for (Map.Entry<String, List<Endpoint>> file : others.entrySet()) {
                    Endpoint other = file.getValue().stream()
                            .filter(o -> o != e && o.hasUsername() && server.equals(server(o, properties)))
                            .findFirst().orElse(null);
                    if (other != null) {
                        answer.add(message(e, other, file.getKey(), catalog));
                        break;
                    }
                }
            }
        } catch (RuntimeException ex) {
            // a shape the check does not know: a check must never fail the validation
        }
        return answer;
    }

    private static String message(Endpoint e, Endpoint other, String otherFile, CamelCatalog catalog) {
        // the login options of the other endpoint that this one does not have, as written there
        Map<String, String> login = new LinkedHashMap<>();
        Set<String> security = securityOptions(catalog, e.scheme());
        if (other.userInfo() && !other.options().containsKey("username")) {
            login.put("username", userOf(other.uri()));
        }
        other.options().forEach((k, v) -> {
            if (security.contains(k) && !e.options().containsKey(k)) {
                login.put(k, v);
            }
        });
        List<String> pairs = new ArrayList<>();
        login.forEach((k, v) -> pairs.add(k + ": " + yamlValue(v)));
        String fix;
        if (e.parameters()) {
            fix = "add the same login under the parameters: of this " + e.eip() + ": " + String.join(", ", pairs);
        } else if (e.query()) {
            StringBuilder query = new StringBuilder();
            login.forEach((k, v) -> query.append('&').append(k).append('=').append(v));
            fix = "add the same login to its uri: " + query;
        } else {
            fix = "write it with the same login: " + e.eip() + ": {uri: " + YamlLines.quoted(e.uri())
                  + ", parameters: {" + String.join(", ", pairs) + "}}";
        }
        String where = otherFile != null
                ? "the " + other.eip() + ": of " + otherFile + " (line " + other.line() + ")"
                : "the " + other.eip() + ": on line " + other.line();
        return "Line " + e.line() + ": " + e.eip() + ": " + e.uri() + " has no username, so it logs in as anonymous,"
               + " while " + where + " logs in to the same server: " + fix;
    }

    /** The options of the security group of the component, such as username and useUserKnownHostsFile. */
    private static Set<String> securityOptions(CamelCatalog catalog, String scheme) {
        try {
            ComponentModel model = catalog != null ? catalog.componentModel(scheme) : null;
            if (model != null) {
                Set<String> answer = new HashSet<>();
                for (ComponentModel.EndpointOptionModel o : model.getEndpointOptions()) {
                    if ("security".equals(o.getGroup()) || o.getLabel() != null && o.getLabel().contains("security")) {
                        answer.add(o.getName());
                    }
                }
                if (!answer.isEmpty()) {
                    return answer;
                }
            }
        } catch (Exception e) {
            // the catalog cannot say: the usual login options
        }
        return SECURITY_OPTIONS;
    }

    /** A value as YAML, quoted when it has a placeholder or a character YAML reads itself. */
    private static String yamlValue(String value) {
        return value.isEmpty() || value.matches(".*[{}:#\\[\\],&*!|>'\"%@`].*") || !value.equals(value.strip())
                ? YamlLines.quoted(value) : value;
    }

    /** The server of the endpoint: its scheme, host and port, placeholders resolved, or null when it has no host. */
    private static String server(Endpoint e, Map<String, String> properties) {
        String host = resolve(e.host(), properties);
        if (host == null || host.isBlank()) {
            return null;
        }
        String port = resolve(e.port(), properties);
        if (port == null || port.isBlank()) {
            port = "sftp".equals(e.scheme()) ? "22" : "21";
        }
        return e.scheme() + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
    }

    /** The text with the {{key}} placeholders the properties have replaced by their values, {{key:default}} too. */
    private static String resolve(String text, Map<String, String> properties) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = properties.get(m.group(1).strip());
            if (value == null) {
                value = m.group(2);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.strip() : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** The remote file endpoints of a YAML route, in the order they are written. */
    private static List<Endpoint> endpoints(String content) {
        List<Endpoint> answer = new ArrayList<>();
        try {
            for (Node root : new Yaml(new SafeConstructor(new LoaderOptions())).composeAll(new StringReader(content))) {
                collect(root, answer);
            }
        } catch (RuntimeException e) {
            // not YAML: the schema check says so
        }
        return answer;
    }

    private static void collect(Node node, List<Endpoint> answer) {
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                String key = scalar(t.getKeyNode());
                if (key != null && ENDPOINT_KEYS.contains(key)) {
                    Endpoint e = endpoint(key, t.getValueNode());
                    if (e != null) {
                        answer.add(e);
                    }
                }
                collect(t.getValueNode(), answer);
            }
        } else if (node instanceof SequenceNode seq) {
            seq.getValue().forEach(n -> collect(n, answer));
        }
    }

    /** The endpoint of an EIP: to: "sftp://...", or to: {uri: sftp, parameters: {...}}; null for another scheme. */
    private static Endpoint endpoint(String eip, Node value) {
        Node uriNode = value instanceof MappingNode ? value(value, "uri") : value;
        String uri = scalar(uriNode);
        if (uri == null) {
            return null;
        }
        uri = uri.strip();
        int colon = uri.indexOf(':');
        String scheme = (colon > 0 ? uri.substring(0, colon) : uri).toLowerCase(Locale.ROOT);
        if (!SCHEMES.contains(scheme)) {
            return null;
        }
        Map<String, String> options = new LinkedHashMap<>();
        String rest = colon > 0 ? uri.substring(colon + 1) : "";
        boolean query = rest.contains("?");
        if (query) {
            for (String pair : rest.substring(rest.indexOf('?') + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    options.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            rest = rest.substring(0, rest.indexOf('?'));
        }
        if (rest.startsWith("//")) {
            rest = rest.substring(2);
        }
        String authority = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        boolean userInfo = authority.contains("@");
        if (userInfo) {
            authority = authority.substring(authority.lastIndexOf('@') + 1);
        }
        // host:port, where the host or port may be a placeholder: the last colon outside {{...}}
        String host = authority;
        String port = null;
        int split = lastColonOutsidePlaceholders(authority);
        if (split >= 0) {
            host = authority.substring(0, split);
            port = authority.substring(split + 1);
        }
        boolean parameters = false;
        if (value(value, "parameters") instanceof MappingNode params) {
            parameters = true;
            for (NodeTuple t : params.getValue()) {
                String k = scalar(t.getKeyNode());
                String v = scalar(t.getValueNode());
                if (k != null) {
                    options.put(k, v != null ? v : "");
                }
            }
        }
        if (options.containsKey("host")) {
            host = options.get("host");
        }
        if (options.containsKey("port")) {
            port = options.get("port");
        }
        return new Endpoint(
                uriNode.getStartMark().getLine() + 1, eip, uri, scheme, host, port, userInfo, options,
                parameters, query);
    }

    private static int lastColonOutsidePlaceholders(String text) {
        int depth = 0;
        int answer = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == ':' && depth == 0) {
                answer = i;
            }
        }
        return answer;
    }

    /** The user of sftp://user:secret@host/dir. */
    private static String userOf(String uri) {
        String rest = uri.substring(uri.indexOf(':') + 1);
        rest = rest.startsWith("//") ? rest.substring(2) : rest;
        String user = rest.substring(0, rest.lastIndexOf('@'));
        return user.contains(":") ? user.substring(0, user.indexOf(':')) : user;
    }

    /** The remote file endpoints of the other YAML route files of the project, by file. */
    private static Map<String, List<Endpoint>> otherFiles(Path directory, String fileName) {
        Map<String, List<Endpoint>> answer = new LinkedHashMap<>();
        Path self = fileName != null ? directory.resolve(fileName).normalize() : null;
        try {
            for (Path p : AuthoringTools.projectFiles(directory)) {
                String rel = AuthoringTools.relativePath(directory, p);
                if (p.normalize().equals(self) || !"yaml".equals(AuthoringTools.fileType(rel))
                        || !"route".equals(AuthoringTools.fileKind(p, rel, "yaml"))) {
                    continue;
                }
                String text = Files.readString(p);
                if (text.contains("ftp")) {
                    answer.put(rel, endpoints(text));
                }
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable project has no other endpoint to compare
        }
        return answer;
    }

    /** The properties of the .properties files of the project directory, as camel run reads them. */
    private static Map<String, String> properties(Path directory) {
        Map<String, String> answer = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(".properties")).sorted().forEach(p -> {
                Properties props = new Properties();
                try (Reader r = Files.newBufferedReader(p)) {
                    props.load(r);
                } catch (IOException e) {
                    // an unreadable file sets nothing
                }
                props.stringPropertyNames().forEach(k -> answer.putIfAbsent(k, props.getProperty(k)));
            });
        } catch (IOException e) {
            // no directory listing: no properties
        }
        return answer;
    }

    private static Node value(Node node, String key) {
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                if (key.equals(scalar(t.getKeyNode()))) {
                    return t.getValueNode();
                }
            }
        }
        return null;
    }

    private static String scalar(Node node) {
        return node instanceof ScalarNode s ? s.getValue() : null;
    }
}
