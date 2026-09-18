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
package org.apache.camel.component.rest.postman.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.component.rest.postman.RestPostmanHelper;
import org.apache.camel.component.rest.postman.model.PostmanAuth;
import org.apache.camel.component.rest.postman.model.PostmanCollection;
import org.apache.camel.component.rest.postman.model.PostmanItem;
import org.apache.camel.component.rest.postman.model.PostmanJson;
import org.apache.camel.component.rest.postman.model.PostmanRequest;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flattens the nested item tree of a collection and indexes it for lookup by the URI fragment.
 * <p>
 * The fragment may select a single request, a folder, or nothing at all, which means the whole collection. Requests are
 * matched before folders, so that the common case of naming a request costs no extra syntax; a trailing {@code /}
 * forces a folder match when a request and a folder share a name.
 */
public final class PostmanRequestIndex {

    /**
     * Folders nest recursively in the schema, so the walk is depth limited to keep a deep or hostile document from
     * overflowing the stack.
     */
    public static final int MAX_FOLDER_DEPTH = 64;

    /**
     * An upper bound on how many requests one collection may contribute, as a denial-of-service guard.
     */
    public static final int MAX_ITEMS = 5000;

    private static final Logger LOG = LoggerFactory.getLogger(PostmanRequestIndex.class);

    private final List<PostmanItem> items;
    private final Map<String, PostmanItem> byId = new LinkedHashMap<>();
    private final Map<String, PostmanItem> byCanonicalId = new LinkedHashMap<>();
    private final Map<String, List<PostmanItem>> bySlug = new LinkedHashMap<>();
    private final Map<String, List<PostmanItem>> byFolder = new LinkedHashMap<>();

    private PostmanRequestIndex(List<PostmanItem> items, Map<String, List<PostmanItem>> folders) {
        this.items = List.copyOf(items);
        this.byFolder.putAll(folders);
        for (PostmanItem item : items) {
            if (item.getId() != null) {
                byId.putIfAbsent(item.getId(), item);
            }
            byCanonicalId.putIfAbsent(item.getCanonicalId(), item);
            bySlug.computeIfAbsent(item.getSlug(), k -> new ArrayList<>()).add(item);
        }
    }

    /**
     * Builds an index over a collection.
     *
     * @param collection    the collection
     * @param requestFilter comma separated Ant style patterns over qualified slugs, {@code !} prefixed to exclude, or
     *                      {@code null} to keep everything
     */
    public static PostmanRequestIndex build(PostmanCollection collection, String requestFilter) {
        Walker walker = new Walker(collection.getVariables(), collection.getAuth());
        walker.walk(collection.getItems(), List.of(), 0);

        List<Raw> kept = walker.raws;
        if (requestFilter != null && !requestFilter.isBlank()) {
            kept = kept.stream().filter(raw -> PostmanFilters.matches(raw.qualifiedSlug, requestFilter)).toList();
        }

        List<PostmanItem> items = assignCanonicalIds(kept);

        // a folder is addressable by its own qualified slug and by every prefix of it, so that selecting an outer
        // folder also runs everything nested inside it
        Map<String, List<PostmanItem>> folders = new LinkedHashMap<>();
        for (PostmanItem item : items) {
            List<String> folderSlugs = slugifyFolders(item.getFolderPath());
            StringBuilder prefix = new StringBuilder();
            for (String folderSlug : folderSlugs) {
                if (!prefix.isEmpty()) {
                    prefix.append('/');
                }
                prefix.append(folderSlug);
                folders.computeIfAbsent(prefix.toString(), k -> new ArrayList<>()).add(item);
            }
        }
        return new PostmanRequestIndex(items, folders);
    }

    /**
     * Resolves a URI fragment.
     *
     * @param  selector the fragment, or {@code null}/empty for the whole collection
     * @return          what was selected, never empty
     */
    public Selection resolve(String selector) {
        if (selector == null || selector.isBlank()) {
            if (items.isEmpty()) {
                throw new IllegalArgumentException("The Postman collection contains no requests");
            }
            return new Selection(items, "the whole collection", false);
        }

        String trimmed = selector.trim();
        if (trimmed.endsWith("/")) {
            // an explicit folder reference, which is how a folder is selected when a request shares its name
            String folder = trimmed.substring(0, trimmed.length() - 1);
            List<PostmanItem> found = byFolder.get(folder);
            if (found == null) {
                throw new IllegalArgumentException(
                        "The Postman collection has no folder `" + folder + "`." + describeFolders());
            }
            return new Selection(found, "folder `" + folder + "`", false);
        }

        // an id recorded by the Postman cloud always wins, as it is unambiguous
        if (RestPostmanHelper.isUuid(trimmed)) {
            PostmanItem found = byId.get(trimmed);
            if (found != null) {
                return new Selection(List.of(found), "request " + found.describe(), true);
            }
        }

        PostmanItem canonical = byCanonicalId.get(trimmed);
        if (canonical != null) {
            return new Selection(List.of(canonical), "request " + canonical.describe(), true);
        }

        List<PostmanItem> slugMatches = bySlug.get(trimmed);
        if (slugMatches != null && slugMatches.size() == 1) {
            PostmanItem found = slugMatches.get(0);
            return new Selection(List.of(found), "request " + found.describe(), true);
        }
        if (slugMatches != null && slugMatches.size() > 1) {
            String candidates = slugMatches.stream()
                    .map(PostmanItem::getCanonicalId)
                    .collect(Collectors.joining("\n\t"));
            throw new IllegalArgumentException(
                    "The Postman collection has " + slugMatches.size() + " requests named `" + trimmed
                                               + "`. Use one of the folder qualified ids instead:\n\t" + candidates);
        }

        List<PostmanItem> folderMatch = byFolder.get(trimmed);
        if (folderMatch != null) {
            return new Selection(folderMatch, "folder `" + trimmed + "`", false);
        }

        throw new IllegalArgumentException(
                "The Postman collection has no request or folder `" + trimmed + "`." + describeAll());
    }

    /**
     * All requests, in document order.
     */
    public List<PostmanItem> getItems() {
        return items;
    }

    /**
     * Lists every request id, for use in a failure message.
     */
    public String describeAll() {
        if (items.isEmpty()) {
            return " The collection contains no requests.";
        }
        return " Requests defined in the collection are:\n\t"
               + items.stream().map(PostmanItem::getCanonicalId).collect(Collectors.joining("\n\t"));
    }

    private String describeFolders() {
        if (byFolder.isEmpty()) {
            return " The collection contains no folders.";
        }
        return " Folders defined in the collection are:\n\t" + String.join("\n\t", byFolder.keySet());
    }

    /**
     * Decides the canonical id of every request: the plain slug when it is unique, the folder qualified slug when it is
     * not, and a numeric suffix in the rare case where even that collides.
     */
    private static List<PostmanItem> assignCanonicalIds(List<Raw> raws) {
        Map<String, Long> slugCounts = raws.stream()
                .collect(Collectors.groupingBy(raw -> raw.slug, Collectors.counting()));

        Set<String> taken = new LinkedHashSet<>();
        List<PostmanItem> answer = new ArrayList<>(raws.size());
        for (Raw raw : raws) {
            String candidate = slugCounts.get(raw.slug) == 1 ? raw.slug : raw.qualifiedSlug;
            String canonical = candidate;
            int suffix = 2;
            while (!taken.add(canonical)) {
                // two requests with the same name in the same folder, which Postman permits
                canonical = candidate + "-" + suffix++;
                LOG.warn("Postman collection has more than one request named {} in the same folder."
                         + " Using `{}` as its id; rename one of them to make routes unambiguous.",
                        raw.name, canonical);
            }
            answer.add(new PostmanItem(
                    raw.json, raw.id, raw.name, raw.folderPath, raw.slug, raw.qualifiedSlug,
                    canonical, raw.request, raw.auth, raw.variables));
        }
        return answer;
    }

    private static List<String> slugifyFolders(List<String> folderPath) {
        List<String> answer = new ArrayList<>(folderPath.size());
        for (int i = 0; i < folderPath.size(); i++) {
            answer.add(RestPostmanHelper.slugify(folderPath.get(i), "folder" + i));
        }
        return answer;
    }

    /**
     * What a URI fragment selected.
     *
     * @param items       the matched requests, never empty
     * @param description a human readable description for logs and error messages
     * @param single      whether exactly one request was named, as opposed to a folder or the whole collection
     */
    public record Selection(List<PostmanItem> items, String description, boolean single) {
    }

    /**
     * A request captured during the tree walk, before canonical ids can be decided, which needs the whole collection.
     */
    private record Raw(JsonObject json, String id, String name, List<String> folderPath, String slug,
            String qualifiedSlug, PostmanRequest request, PostmanAuth auth, Map<String, String> variables) {
    }

    /**
     * Depth first walk of the item tree, carrying the inherited variable scope and auth block down as it descends.
     */
    private static final class Walker {

        private final List<Raw> raws = new ArrayList<>();
        private final Map<String, String> rootVariables;
        private final PostmanAuth rootAuth;

        Walker(Map<String, String> rootVariables, PostmanAuth rootAuth) {
            this.rootVariables = rootVariables;
            this.rootAuth = rootAuth;
        }

        void walk(List<?> nodes, List<String> folderPath, int depth) {
            walk(nodes, folderPath, depth, rootVariables, rootAuth);
        }

        private void walk(
                List<?> nodes, List<String> folderPath, int depth,
                Map<String, String> inheritedVariables, PostmanAuth inheritedAuth) {
            if (depth > MAX_FOLDER_DEPTH) {
                throw new IllegalArgumentException(
                        "Postman collection nests folders more than " + MAX_FOLDER_DEPTH + " deep at "
                                                   + String.join(" / ", folderPath));
            }
            int index = 0;
            for (Object node : nodes) {
                JsonObject entry = PostmanJson.asObject(node);
                if (entry == null) {
                    continue;
                }
                String name = PostmanJson.asString(entry.get("name"));

                Map<String, String> scope = new LinkedHashMap<>(inheritedVariables);
                scope.putAll(PostmanCollection.variablesOf(entry));
                PostmanAuth auth = PostmanAuth.parse(entry.get("auth"));
                PostmanAuth effectiveAuth = auth != null ? auth : inheritedAuth;

                Object children = entry.get("item");
                if (children instanceof List<?> list) {
                    List<String> nested = new ArrayList<>(folderPath);
                    nested.add(name != null ? name : "folder" + index);
                    walk(list, nested, depth + 1, scope, effectiveAuth);
                } else {
                    PostmanRequest request = PostmanRequest.parse(entry.get("request"));
                    if (request == null) {
                        LOG.debug("Skipping Postman item {} because it has neither a request nor nested items", name);
                    } else {
                        addRequest(entry, name, folderPath, index, request, effectiveAuth, scope);
                    }
                }
                index++;
            }
        }

        private void addRequest(
                JsonObject entry, String name, List<String> folderPath, int index,
                PostmanRequest request, PostmanAuth auth, Map<String, String> scope) {
            if (raws.size() >= MAX_ITEMS) {
                throw new IllegalArgumentException(
                        "Postman collection contains more than " + MAX_ITEMS + " requests");
            }
            String slug = RestPostmanHelper.slugify(name, "request" + index);
            List<String> folderSlugs = slugifyFolders(folderPath);
            String qualifiedSlug = folderSlugs.isEmpty() ? slug : String.join("/", folderSlugs) + "/" + slug;

            // url.variable supplies defaults for the :name path markers, so it belongs in the request's own scope
            Map<String, String> variables = new LinkedHashMap<>(scope);
            request.getUrl().getPathVariables().stream()
                    .filter(variable -> !variable.disabled())
                    .forEach(variable -> variables.put(variable.key(),
                            variable.value() != null ? variable.value() : ""));

            raws.add(new Raw(
                    entry, PostmanJson.asString(entry.get("id")), name != null ? name : slug,
                    folderPath, slug, qualifiedSlug, request, auth, variables));
        }
    }
}
