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
package org.apache.camel.component.salesforce.codegen;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.SObject;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.SyncResponseCallback;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;

public final class ObjectDescriptions {

    private final RestClient client;

    private final Map<String, SObjectDescription> descriptions = new ConcurrentHashMap<>();

    private final long responseTimeout;

    public ObjectDescriptions(final RestClient client, final long responseTimeout, final String[] includes,
                              final String includePattern, final String[] excludes,
                              final String excludePattern, final Logger log)
                                                                             throws Exception {
        this.client = client;
        this.responseTimeout = responseTimeout;

        fetchSpecifiedDescriptions(includes, includePattern, excludes, excludePattern, log);
    }

    int count() {
        return descriptions.size();
    }

    SObjectDescription descriptionOf(final String name) {
        return descriptions.computeIfAbsent(name, this::fetchDescriptionOf);
    }

    boolean hasDescription(final String name) {
        return descriptions.containsKey(name);
    }

    List<SObjectField> externalIdsOf(final String name) {
        return descriptionOf(name).getFields().stream().filter(SObjectField::isExternalId).collect(Collectors.toList());
    }

    boolean hasExternalIds(final String name) {
        return descriptionOf(name).getFields().stream().anyMatch(SObjectField::isExternalId);
    }

    public Iterable<SObjectDescription> fetched() {
        return descriptions.values();
    }

    private SObjectDescription fetchDescriptionOf(final String name) {
        try {
            final ObjectMapper mapper = JsonUtils.createObjectMapper();
            final SyncResponseCallback callback = new SyncResponseCallback();

            client.getDescription(name, Collections.emptyMap(), callback);
            if (!callback.await(responseTimeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout waiting for getDescription for sObject " + name);
            }
            final SalesforceException ex = callback.getException();
            if (ex != null) {
                throw ex;
            }
            final SObjectDescription description = mapper.readValue(callback.getResponse(), SObjectDescription.class);

            // remove some of the unused used metadata
            // properties in order to minimize the code size
            // for CAMEL-11310
            return description.prune();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while getting SObject description for '" + name + "'", e);
        } catch (final Exception e) {
            throw new IllegalStateException("Error getting SObject description for '" + name + "': " + e.getMessage(), e);
        }
    }

    private void fetchSpecifiedDescriptions(
            final String[] includes, final String includePattern, final String[] excludes, final String excludePattern,
            final Logger log)
            throws Exception {
        // use Jackson json
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        // call getGlobalObjects to get all SObjects
        final Set<String> objectNames = new TreeSet<>();
        final SyncResponseCallback callback = new SyncResponseCallback();
        try {
            log.info("Getting Salesforce Objects...");
            client.getGlobalObjects(Collections.emptyMap(), callback);
            if (!callback.await(responseTimeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout waiting for getGlobalObjects!");
            }
            final SalesforceException ex = callback.getException();
            if (ex != null) {
                throw ex;
            }
            final GlobalObjects globalObjects = mapper.readValue(callback.getResponse(), GlobalObjects.class);

            // create a list of object names
            for (final SObject sObject : globalObjects.getSobjects()) {
                objectNames.add(sObject.getName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while getting global Objects", e);
        } catch (final Exception e) {
            throw new RuntimeException("Error getting global Objects: " + e.getMessage(), e);
        }

        // check if we are generating POJOs for all objects or not
        if (includes != null && includes.length > 0 || excludes != null && excludes.length > 0
                || ObjectHelper.isNotEmpty(includePattern)
                || ObjectHelper.isNotEmpty(excludePattern)) {

            filterObjectNames(objectNames, includes, includePattern, excludes, excludePattern, log);

        } else {
            log.warn(String.format("Generating Java classes for all %s Objects, this may take a while...", objectNames.size()));
        }

        log.info("Retrieving Object descriptions...");
        for (final String name : objectNames) {
            descriptionOf(name);
        }
    }

    private static void filterObjectNames(
            final Set<String> objectNames, final String[] includes, final String includePattern, final String[] excludes,
            final String excludePattern, final Logger log)
            throws Exception {
        log.info("Looking for matching Object names...");
        // create a list of accepted names
        final Set<String> includedNames = new HashSet<>();
        if (includes != null && includes.length > 0) {
            for (String name : includes) {
                name = name.trim();
                if (name.isEmpty()) {
                    throw new RuntimeException("Invalid empty name in includes");
                }
                includedNames.add(name);
            }
        }

        final Set<String> excludedNames = new HashSet<>();
        if (excludes != null && excludes.length > 0) {
            for (String name : excludes) {
                name = name.trim();
                if (name.isEmpty()) {
                    throw new RuntimeException("Invalid empty name in excludes");
                }
                excludedNames.add(name);
            }
        }

        // check whether a pattern is in effect
        Pattern incPattern;
        if (includePattern != null && !includePattern.isBlank()) {
            incPattern = Pattern.compile(includePattern.trim());
        } else if (includedNames.isEmpty()) {
            // include everything by default if no include names are set
            incPattern = Defaults.MATCH_EVERYTHING_PATTERN;
        } else {
            // include nothing by default if include names are set
            incPattern = Defaults.MATCH_NOTHING_PATTERN;
        }

        // check whether a pattern is in effect
        Pattern excPattern;
        if (excludePattern != null && !excludePattern.isBlank()) {
            excPattern = Pattern.compile(excludePattern.trim());
        } else {
            // exclude nothing by default
            excPattern = Defaults.MATCH_NOTHING_PATTERN;
        }

        final Set<String> acceptedNames = new HashSet<>();
        for (final String name : objectNames) {
            // name is included, or matches include pattern
            // and is not excluded and does not match exclude pattern
            if ((includedNames.contains(name) || incPattern.matcher(name).matches()) && !excludedNames.contains(name)
                    && !excPattern.matcher(name).matches()) {
                acceptedNames.add(name);
            }
        }
        objectNames.clear();
        objectNames.addAll(acceptedNames);

        log.info(String.format("Found %s matching Objects", objectNames.size()));
    }
}
