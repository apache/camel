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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        List<ResolverEntry> resolverEntries = new ArrayList<>();
        resolverEntries.addAll(loadResolversFromProvider());
        resolverEntries.addAll(loadResolversFromRegistry());

        addResolverEntries(resolverEntries);
        resolversLoaded = true;
    }

    @Override
    public void stop() {
        this.constructors.clear();
        this.resolvers.removeIf(entry -> entry.source == ResolverSource.PROVIDER || entry.source == ResolverSource.REGISTRY);
        this.resolversLoaded = false;
    }

    private List<ResolverEntry> loadResolversFromProvider() {
        YamlDeserializerResolverProvider provider = getResolverProvider();
        return loadResolverEntries(provider.findResolvers(getCamelContext()), ResolverSource.PROVIDER);
    }

    private YamlDeserializerResolverProvider getResolverProvider() {
        YamlDeserializerResolverProvider provider = getCamelContext().getCamelContextExtension()
                .getContextPlugin(YamlDeserializerResolverProvider.class);
        return provider != null ? provider : new DefaultYamlDeserializerResolverProvider();
    }

    private List<ResolverEntry> loadResolversFromRegistry() {
        return loadResolverEntries(getCamelContext().getRegistry().findByTypeWithName(YamlDeserializerResolver.class),
                ResolverSource.REGISTRY);
    }

    private List<ResolverEntry> loadResolverEntries(
            Map<String, YamlDeserializerResolver> resolvers, ResolverSource source) {
        if (resolvers == null) {
            throw new YamlDeserializationException(
                    "YAML deserializer resolver " + source + " returned a null resolver map");
        }

        List<ResolverEntry> resolverEntries = new ArrayList<>(resolvers.size());
        for (Map.Entry<String, YamlDeserializerResolver> entry : resolvers.entrySet()) {
            if (entry.getKey() == null) {
                throw new YamlDeserializationException(
                        "YAML deserializer resolver " + source + " returned a null resolver name");
            }
            YamlDeserializerResolver resolver = entry.getValue();
            if (resolver == null) {
                throw new YamlDeserializationException(
                        "YAML deserializer resolver " + source + " returned a null resolver for " + entry.getKey());
            }
            CamelContextAware.trySetCamelContext(resolver, getCamelContext());
            resolverEntries.add(new ResolverEntry(resolver, source, entry.getKey()));
        }
        return resolverEntries;
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

    private int compareResolverEntries(ResolverEntry first, ResolverEntry second) {
        int result = compareBuiltInBoundary(first, second);
        if (result != 0) {
            return result;
        }

        result = OrderedComparator.get().compare(first.resolver, second.resolver);
        if (result != 0) {
            return result;
        }

        result = Integer.compare(first.source.rank, second.source.rank);
        if (result != 0) {
            return result;
        }

        result = first.resolver.getClass().getName().compareTo(second.resolver.getClass().getName());
        if (result != 0) {
            return result;
        }

        return first.name.compareTo(second.name);
    }

    private int compareBuiltInBoundary(ResolverEntry first, ResolverEntry second) {
        if (first.isBuiltIn() && !second.isBuiltIn()) {
            return second.overridesBuiltIns() ? 1 : -1;
        }
        if (!first.isBuiltIn() && second.isBuiltIn()) {
            return first.overridesBuiltIns() ? -1 : 1;
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

        private boolean isBuiltIn() {
            return source == ResolverSource.BUILT_IN;
        }

        private boolean overridesBuiltIns() {
            return resolver.getOrder() < YamlDeserializerResolver.ORDER_DEFAULT;
        }
    }

    private enum ResolverSource {
        BUILT_IN(0),
        MANUAL(1),
        REGISTRY(2),
        PROVIDER(3);

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

                    final ConstructNode constructor = resolve(n, type.getName());
                    return construct(newNode, type.getName(), constructor);
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
        final ConstructNode constructor = resolve(node, id);

        return CamelContextAware.trySetCamelContext(
                (Node n) -> {
                    YamlSupport.setProperty(
                            n,
                            YamlDeserializationContext.class.getName(),
                            YamlDeserializationContext.this);

                    Node valueNode = ((MappingNode) n).getValue().get(0).getValueNode();
                    return construct(valueNode, id, constructor);
                },
                camelContext);
    }

    public ConstructNode resolve(Node node, String id) {
        return constructors.computeIfAbsent(id, nodeId -> resolveConstructor(node, nodeId));
    }

    private ConstructNode resolveConstructor(Node node, String id) {
        for (ResolverEntry entry : resolvers) {
            ConstructNode constructor;
            try {
                constructor = entry.resolver.resolve(id);
            } catch (YamlDeserializationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new YamlDeserializationException(
                        node,
                        "Error resolving YAML node id: " + id + " using YAML deserializer resolver " + entry.name,
                        e);
            }
            if (constructor != null) {
                return constructor;
            }
        }

        throw new UnknownNodeIdException(node, id);
    }

    private Object construct(Node node, String id, ConstructNode constructor) {
        try {
            return constructor.construct(node);
        } catch (DuplicateKeyException | UnknownNodeIdException e) {
            throw e;
        } catch (YamlDeserializationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new YamlDeserializationException(node, "Error constructing YAML node id: " + id, e);
        }
    }
}
