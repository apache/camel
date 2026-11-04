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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * The option and header names of the catalog, by where they are: the options of the EIPs, components, languages and
 * data formats, and the message headers of the components (CAMEL-26572). A lookup that matches nothing says where an
 * option of that name is (apiContextPath was looked for on the rest component 23 times in one step, it is an option of
 * restConfiguration), and camel_catalog_find finds a header by its name or its constant. Built once per catalog
 * version, as it reads every model of the catalog.
 */
final class CatalogNameIndex {

    /** How many places the line of {@link #where(String)} names, so it stays short. */
    static final int MAX_PLACES = 4;

    /** An option of an EIP, component, language or data format, or a message header of a component. */
    record Place(String name, String kind, String artifact, boolean header) {
    }

    /** A message header of a component. */
    record Header(String component, ComponentModel.EndpointHeaderModel model) {
    }

    private static volatile CatalogNameIndex index;

    private final String version;
    /** The places of an option or header, by its name in lower case. */
    private final Map<String, List<Place>> places = new HashMap<>();
    /** The headers by their name and by their constant (AWS2S3Constants#KEY), as a find term is compared. */
    private final Map<String, List<Header>> headers = new HashMap<>();

    private CatalogNameIndex(CamelCatalog catalog) {
        this.version = catalog.getCatalogVersion();
        for (String name : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(name);
            if (model != null) {
                addOptions(model.getOptions(), "eip", model.getName());
            }
        }
        for (String name : catalog.findComponentNames()) {
            ComponentModel model = catalog.componentModel(name);
            if (model == null) {
                continue;
            }
            // an option of both the endpoint and the component is one place
            addOptions(model.getEndpointOptions(), "component", model.getScheme());
            addOptions(model.getComponentOptions(), "component", model.getScheme());
            if (model.getEndpointHeaders() != null) {
                for (ComponentModel.EndpointHeaderModel h : model.getEndpointHeaders()) {
                    addHeader(model.getScheme(), h);
                }
            }
        }
        for (String name : catalog.findLanguageNames()) {
            LanguageModel model = catalog.languageModel(name);
            if (model != null) {
                addOptions(model.getOptions(), "language", model.getName());
            }
        }
        for (String name : catalog.findDataFormatNames()) {
            DataFormatModel model = catalog.dataFormatModel(name);
            if (model != null) {
                addOptions(model.getOptions(), "dataformat", model.getName());
            }
        }
    }

    /** The index of the catalog, built on first use and again when a catalog of another version asks. */
    static CatalogNameIndex of(CamelCatalog catalog) {
        CatalogNameIndex answer = index;
        if (answer == null || !Objects.equals(answer.version, catalog.getCatalogVersion())) {
            answer = new CatalogNameIndex(catalog);
            index = answer;
        }
        return answer;
    }

    private void addOptions(List<? extends BaseOptionModel> options, String kind, String artifact) {
        if (options == null) {
            return;
        }
        for (BaseOptionModel opt : options) {
            if (opt.getName() != null) {
                add(new Place(opt.getName(), kind, artifact, false));
            }
        }
    }

    private void addHeader(String component, ComponentModel.EndpointHeaderModel h) {
        if (h.getName() == null) {
            return;
        }
        add(new Place(h.getName(), "component", component, true));
        Header header = new Header(component, h);
        headers.computeIfAbsent(key(h.getName()), k -> new ArrayList<>()).add(header);
        String constant = h.getConstantName();
        int hash = constant != null ? constant.indexOf('#') : -1;
        if (hash > 0) {
            // AWS2S3Constants KEY, AWS2S3Constants.KEY and the full name of the constant
            String type = constant.substring(0, hash);
            String simple = type.substring(type.lastIndexOf('.') + 1);
            headers.computeIfAbsent(key(simple + constant.substring(hash + 1)), k -> new ArrayList<>()).add(header);
            headers.computeIfAbsent(key(constant), k -> new ArrayList<>()).add(header);
        }
    }

    private void add(Place place) {
        List<Place> list = places.computeIfAbsent(place.name().toLowerCase(Locale.ROOT), k -> new ArrayList<>());
        if (!list.contains(place)) {
            list.add(place);
        }
    }

    /** A term as camel_catalog_find compares it: lower case, letters and digits only. */
    private static String key(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The message headers whose name or constant is the term, by header name in the order of the catalog, each with the
     * components that have it; empty when there is none. The components that share the most common constant come first:
     * CamelFileName is FileConstants#FILE_NAME for file, ftp and sftp, and alibaba-oss is first in the catalog.
     */
    Map<String, List<Header>> headersNamed(String term) {
        Map<String, List<Header>> answer = new LinkedHashMap<>();
        for (Header h : headers.getOrDefault(key(term), List.of())) {
            answer.computeIfAbsent(h.model().getName(), k -> new ArrayList<>()).add(h);
        }
        for (List<Header> same : answer.values()) {
            Map<String, Integer> count = new HashMap<>();
            same.forEach(h -> count.merge(String.valueOf(h.model().getConstantName()), 1, Integer::sum));
            same.sort(Comparator.comparingInt(h -> -count.get(String.valueOf(h.model().getConstantName()))));
        }
        return answer;
    }

    /**
     * Where an option or a message header of that name is, as a short line with the call that shows it, such as
     * "apiContextPath is an option of restConfiguration (eip): camel_catalog_doc name=restConfiguration kind=eip
     * optionsFilter=apiContextPath shows it"; null when nothing in the catalog has that name.
     */
    String where(String name) {
        List<Place> found = places.get(name.trim().toLowerCase(Locale.ROOT));
        if (found == null || found.isEmpty()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        List<String> headerOf = new ArrayList<>();
        for (Place p : found) {
            if (p.header()) {
                headerOf.add(p.artifact());
            } else {
                options.add(p.artifact() + " (" + p.kind() + ")");
            }
        }
        Place first = found.get(0);
        StringBuilder sb = new StringBuilder(first.name()).append(" is ");
        if (!options.isEmpty()) {
            sb.append("an option of ").append(few(options));
        }
        if (!headerOf.isEmpty()) {
            sb.append(options.isEmpty() ? "" : ", and ").append("a message header of ").append(few(headerOf));
        }
        boolean oneKind = found.stream().allMatch(p -> p.kind().equals(first.kind()));
        sb.append(": camel_catalog_doc name=").append(found.size() == 1 ? first.artifact() : "<one of them>")
                .append(" kind=").append(oneKind ? first.kind() : "<its kind>")
                .append(" optionsFilter=").append(first.name()).append(" shows it");
        return sb.toString();
    }

    /** The names with "and" before the last, or the first few and how many more. */
    private static String few(List<String> names) {
        if (names.size() > MAX_PLACES) {
            return String.join(", ", names.subList(0, MAX_PLACES - 1)) + " and " + (names.size() - MAX_PLACES + 1)
                   + " more";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
