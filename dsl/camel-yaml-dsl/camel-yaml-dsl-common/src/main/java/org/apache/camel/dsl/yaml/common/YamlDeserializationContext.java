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
package org.apache.camel.dsl.yaml.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;
import org.apache.camel.dsl.yaml.common.exception.DuplicateKeyException;
import org.apache.camel.dsl.yaml.common.exception.UnknownNodeIdException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;

public class YamlDeserializationContext extends StandardConstructor implements CamelContextAware, Service {

    private final List<ResolverEntry> resolvers;
    private final Map<String, ConstructNode> constructors;
    private CamelContext camelContext;
    private Resource resource;
    private boolean compactNotationWarn = true;
    private boolean compactNotationWarned;
    private boolean resolversLoaded;

    public YamlDeserializationContext(LoadSettings settings) {
        super(settings);
        this.resolvers = new ArrayList<>();
        this.constructors = new HashMap<>();
    }

    public void addResolver(YamlDeserializerResolver resolver) {
        addResolver(resolver, ResolverSource.MANUAL, null);
    }

    /**
     * Adds a resolver owned by the YAML DSL runtime itself.
     * <p/>
     * Built-in resolvers are protected from accidental classpath or registry shadowing. External resolvers can still
     * override built-in ids by using an order lower than {@link YamlDeserializerResolver#ORDER_DEFAULT}.
     */
    public void addBuiltinResolver(YamlDeserializerResolver resolver) {
        addResolver(resolver, ResolverSource.BUILT_IN, null);
    }

    public void addResolvers(YamlDeserializerResolver... resolvers) {
        addResolvers(Arrays.asList(resolvers));
    }

    public void addResolvers(Collection<YamlDeserializerResolver> resolvers) {
        for (YamlDeserializerResolver resolver : resolvers) {
            addResolver(resolver, ResolverSource.MANUAL, null);
        }
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public boolean isCompactNotationWarn() {
        return compactNotationWarn;
    }

    public void setCompactNotationWarn(boolean compactNotationWarn) {
        this.compactNotationWarn = compactNotationWarn;
    }

    public void warnCompactNotationOnce(Logger log) {
        if (compactNotationWarn && !compactNotationWarned) {
            compactNotationWarned = true;
            String loc = resource != null ? resource.getLocation() : "unknown";
            log.warn("YAML DSL compact notation detected in: {}."
                     + " It is recommended to use canonical/normalized YAML DSL notation"
                     + " which is more tooling and AI friendly."
                     + " Use Camel CLI to normalize: camel validate normalize <file>",
                    loc);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected Optional<ConstructNode> findConstructorFor(Node node) {
        ConstructNode ctor = resolve(node);
        if (ctor != null) {
            return Optional.of(ctor);
        }

        return super.findConstructorFor(node);
    }

    @Override
    public void start() {
        loadResolvers();
    }

    private void loadResolvers() {
        ObjectHelper.notNull(camelContext, "camel context");

        if (resolversLoaded) {
            return;
        }

        addResolverEntries(loadResolversFromClasspath());
        addResolverEntries(loadResolversFromRegistry());
        resolversLoaded = true;
    }

    @Override
    public void stop() {
        this.constructors.clear();
        this.resolvers.removeIf(entry -> entry.source == ResolverSource.CLASSPATH || entry.source == ResolverSource.REGISTRY);
        this.resolversLoaded = false;
    }

    private List<ResolverEntry> loadResolversFromClasspath() {
        List<String> resolverClassNames;
        resolverClassNames = findResolverClassNames();

        List<ResolverEntry> discoveredResolvers = new ArrayList<>(resolverClassNames.size());
        for (String resolverClassName : resolverClassNames) {
            discoveredResolvers
                    .add(new ResolverEntry(newResolver(resolverClassName), ResolverSource.CLASSPATH, resolverClassName));
        }
        discoveredResolvers.sort(this::compareResolverEntries);
        return discoveredResolvers;
    }

    private List<ResolverEntry> loadResolversFromRegistry() {
        Map<String, YamlDeserializerResolver> resolvers
                = getCamelContext().getRegistry().findByTypeWithName(YamlDeserializerResolver.class);
        List<ResolverEntry> answer = new ArrayList<>(resolvers.size());
        for (Map.Entry<String, YamlDeserializerResolver> entry : resolvers.entrySet()) {
            answer.add(new ResolverEntry(entry.getValue(), ResolverSource.REGISTRY, entry.getKey()));
        }
        answer.sort(this::compareResolverEntries);
        return answer;
    }

    private YamlDeserializerResolver newResolver(String resolverClassName) {
        try {
            Class<YamlDeserializerResolver> type
                    = getCamelContext().getClassResolver().resolveMandatoryClass(resolverClassName,
                            YamlDeserializerResolver.class);
            YamlDeserializerResolver resolver = getCamelContext().getInjector().newInstance(type, false);
            CamelContextAware.trySetCamelContext(resolver, getCamelContext());
            return resolver;
        } catch (Exception e) {
            String message = "Error loading YAML deserializer resolver " + resolverClassName + " from "
                             + YamlDeserializerResolver.RESOURCE_PATH;
            throw new YamlDeserializationException(
                    message,
                    e);
        }
    }

    private List<String> findResolverClassNames() {
        Set<String> resolverClassNames = new LinkedHashSet<>();

        for (URL url : findResolverResources()) {
            try (BufferedReader reader
                    = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int comment = line.indexOf('#');
                    if (comment != -1) {
                        line = line.substring(0, comment);
                    }
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        resolverClassNames.add(line);
                    }
                }
            } catch (IOException e) {
                throw new YamlDeserializationException(
                        "Error reading YAML deserializer resolver resource " + url + " from "
                                                       + YamlDeserializerResolver.RESOURCE_PATH,
                        e);
            }
        }

        List<String> sortedResolverClassNames = new ArrayList<>(resolverClassNames);
        Collections.sort(sortedResolverClassNames);
        return sortedResolverClassNames;
    }

    private Set<URL> findResolverResources() {
        Set<URL> answer = new LinkedHashSet<>();

        try {
            addResolverResources(answer,
                    getCamelContext().getClassResolver().loadAllResourcesAsURL(YamlDeserializerResolver.RESOURCE_PATH));
            addResolverResources(answer, getCamelContext().getApplicationContextClassLoader());
            for (ClassLoader classLoader : getCamelContext().getClassResolver().getClassLoaders()) {
                addResolverResources(answer, classLoader);
            }
        } catch (IOException e) {
            throw new YamlDeserializationException(
                    "Error locating YAML deserializer resolvers from " + YamlDeserializerResolver.RESOURCE_PATH, e);
        }

        return answer;
    }

    private static void addResolverResources(Set<URL> answer, ClassLoader classLoader) throws IOException {
        if (classLoader != null) {
            addResolverResources(answer, classLoader.getResources(YamlDeserializerResolver.RESOURCE_PATH));
        }
    }

    private static void addResolverResources(Set<URL> answer, Enumeration<URL> resources) {
        if (resources != null) {
            while (resources.hasMoreElements()) {
                answer.add(resources.nextElement());
            }
        }
    }

    private void addResolver(YamlDeserializerResolver resolver, ResolverSource source, String name) {
        this.resolvers.add(new ResolverEntry(resolver, source, name));
        sortResolverEntries();
        this.constructors.clear();
    }

    private void addResolverEntries(Collection<ResolverEntry> entries) {
        this.resolvers.addAll(entries);
        sortResolverEntries();
        this.constructors.clear();
    }

    private void sortResolverEntries() {
        this.resolvers.sort(this::compareResolverEntries);
    }

    private int compareResolverEntries(ResolverEntry entry1, ResolverEntry entry2) {
        int result = compareBuiltInBoundary(entry1, entry2);
        if (result != 0) {
            return result;
        }

        result = OrderedComparator.get().compare(entry1.resolver, entry2.resolver);
        if (result != 0) {
            return result;
        }

        result = Integer.compare(entry1.source.rank, entry2.source.rank);
        if (result != 0) {
            return result;
        }

        result = entry1.resolver.getClass().getName().compareTo(entry2.resolver.getClass().getName());
        if (result != 0) {
            return result;
        }

        return entry1.name.compareTo(entry2.name);
    }

    private int compareBuiltInBoundary(ResolverEntry entry1, ResolverEntry entry2) {
        if (entry1.source == ResolverSource.BUILT_IN && entry2.source != ResolverSource.BUILT_IN) {
            return entry2.resolver.getOrder() < YamlDeserializerResolver.ORDER_DEFAULT ? 1 : -1;
        }
        if (entry1.source != ResolverSource.BUILT_IN && entry2.source == ResolverSource.BUILT_IN) {
            return entry1.resolver.getOrder() < YamlDeserializerResolver.ORDER_DEFAULT ? -1 : 1;
        }
        return 0;
    }

    private static final class ResolverEntry {
        private final YamlDeserializerResolver resolver;
        private final ResolverSource source;
        private final String name;

        private ResolverEntry(YamlDeserializerResolver resolver, ResolverSource source, String name) {
            this.resolver = resolver;
            this.source = source;
            this.name = name != null ? name : resolver.getClass().getName();
        }
    }

    private enum ResolverSource {
        BUILT_IN(0),
        MANUAL(1),
        REGISTRY(2),
        CLASSPATH(3);

        private final int rank;

        ResolverSource(int rank) {
            this.rank = rank;
        }
    }

    // *********************************
    //
    // Construct
    //
    // *********************************

    public Object construct(Node key, Node val) {
        return mandatoryResolve(key).construct(val);
    }

    public <T> T construct(Node key, Node val, Class<T> type) {
        Object result = construct(key, val);
        if (result == null) {
            return null;
        }

        return type.cast(result);
    }

    public <T> T construct(Node node, Class<T> type) {
        ConstructNode constructor = resolve(type);
        if (constructor == null) {
            throw new YamlDeserializationException(node, "Unable to find constructor for node");
        }
        Object result = constructor.construct(node);
        if (result == null) {
            return null;
        }

        return type.cast(result);
    }

    // *********************************
    //
    // Resolve
    //
    // *********************************

    public ConstructNode resolve(Class<?> type) {
        return CamelContextAware.trySetCamelContext(
                (Node n) -> {
                    Node newNode = YamlSupport.setProperty(
                            n,
                            YamlDeserializationContext.class.getName(),
                            YamlDeserializationContext.this);

                    final ConstructNode answer = resolve(n, type.getName());
                    return answer.construct(newNode);
                },
                camelContext);
    }

    public ConstructNode mandatoryResolve(Node node) {
        ConstructNode constructor = resolve(node);
        if (constructor == null) {
            throw new YamlDeserializationException(node, "Unable to find constructor for node");
        }

        return constructor;
    }

    public ConstructNode resolve(Node node) {
        if (node.getNodeType() != NodeType.MAPPING) {
            return null;
        }

        MappingNode mn = (MappingNode) node;
        if (mn.getValue().size() > 1) {
            throw new DuplicateKeyException(node, mn.getValue());
        } else if (mn.getValue().size() != 1) {
            return null;
        }

        Node key = mn.getValue().get(0).getKeyNode();
        if (key.getNodeType() != NodeType.SCALAR) {
            return null;
        }

        final String id = ((ScalarNode) key).getValue();
        final ConstructNode answer = resolve(node, id);

        return CamelContextAware.trySetCamelContext(
                (Node n) -> {
                    YamlSupport.setProperty(
                            n,
                            YamlDeserializationContext.class.getName(),
                            YamlDeserializationContext.this);

                    return answer.construct(
                            ((MappingNode) n).getValue().get(0).getValueNode());
                },
                camelContext);
    }

    public ConstructNode resolve(Node node, String id) {
        return constructors.computeIfAbsent(id, (String s) -> {
            ConstructNode answer = null;

            for (ResolverEntry entry : resolvers) {
                answer = entry.resolver.resolve(id);
                if (answer != null) {
                    break;
                }
            }

            if (answer == null) {
                throw new UnknownNodeIdException(node, id);
            }

            return answer;
        });
    }
}
