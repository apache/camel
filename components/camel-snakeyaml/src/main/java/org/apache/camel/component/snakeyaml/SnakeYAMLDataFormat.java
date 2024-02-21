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
package org.apache.camel.component.snakeyaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.component.snakeyaml.custom.CustomClassLoaderConstructor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.inspector.TagInspector;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Marshal and unmarshal Java objects to and from YAML using <a href="http://www.snakeyaml.org">SnakeYAML</a>
 */
@Dataformat("snakeYaml")
public final class SnakeYAMLDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private CamelContext camelContext;
    private final ThreadLocal<WeakReference<Yaml>> yamlCache;
    private Function<CamelContext, BaseConstructor> constructor;
    private Function<CamelContext, Representer> representer;
    private Function<CamelContext, DumperOptions> dumperOptions;
    private Function<CamelContext, Resolver> resolver;
    private ClassLoader classLoader;
    private String unmarshalTypeName;
    private Class<?> unmarshalType;
    private List<TypeDescription> typeDescriptions;
    private ConcurrentMap<Class<?>, Tag> classTags;
    private boolean useApplicationContextClassLoader = true;
    private boolean prettyFlow;
    private boolean allowAnyType;
    private List<TypeFilter> typeFilters;
    private int maxAliasesForCollections = 50;
    private boolean allowRecursiveKeys;

    public SnakeYAMLDataFormat() {
        this(null);
    }

    public SnakeYAMLDataFormat(Class<?> type) {
        this.yamlCache = new ThreadLocal<>();
        this.constructor = this::defaultConstructor;
        this.representer = this::defaultRepresenter;
        this.dumperOptions = this::defaultDumperOptions;
        this.resolver = this::defaultResolver;

        if (type != null) {
            this.unmarshalType = type;
            this.typeFilters = new CopyOnWriteArrayList<>();
            this.typeFilters.add(TypeFilters.types(type));
        }
    }

    @Override
    public String getDataFormatName() {
        return "snakeYaml";
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
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        try (OutputStreamWriter osw = new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange))) {
            getYaml(exchange.getContext()).dump(graph, osw);
        }
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        Class<?> unmarshalObjectType = unmarshalType != null ? unmarshalType : Object.class;

        if (body instanceof String s) {
            return getYaml(exchange.getContext()).loadAs(s, unmarshalObjectType);
        } else if (body instanceof Reader r) {
            return getYaml(exchange.getContext()).loadAs(r, unmarshalObjectType);
        } else {
            // fallback to InputStream
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            Reader r = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
            return getYaml(exchange.getContext()).loadAs(r, unmarshalObjectType);
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (unmarshalTypeName != null && unmarshalType == null) {
            setUnmarshalType(camelContext.getClassResolver().resolveClass(unmarshalTypeName));
        }
    }

    protected Yaml getYaml(CamelContext context) {
        Yaml yaml = null;
        WeakReference<Yaml> ref = yamlCache.get();

        if (ref != null) {
            yaml = ref.get();
        }

        if (yaml == null) {
            LoaderOptions options = new LoaderOptions();
            options.setTagInspector(new TrustedTagInspector());
            options.setAllowRecursiveKeys(allowRecursiveKeys);
            options.setMaxAliasesForCollections(maxAliasesForCollections);

            yaml = new Yaml(
                    this.constructor.apply(context),
                    this.representer.apply(context),
                    this.dumperOptions.apply(context),
                    options,
                    this.resolver.apply(context));

            yamlCache.set(new WeakReference<>(yaml));
        }

        return yaml;
    }

    public Function<CamelContext, BaseConstructor> getConstructor() {
        return constructor;
    }

    /**
     * BaseConstructor to construct incoming documents.
     */
    public void setConstructor(Function<CamelContext, BaseConstructor> constructor) {
        this.constructor = constructor;
    }

    public Function<CamelContext, Representer> getRepresenter() {
        return representer;
    }

    /**
     * Representer to emit outgoing objects.
     */
    public void setRepresenter(Function<CamelContext, Representer> representer) {
        this.representer = representer;
    }

    public Function<CamelContext, DumperOptions> getDumperOptions() {
        return dumperOptions;
    }

    /**
     * DumperOptions to configure outgoing objects.
     */
    public void setDumperOptions(Function<CamelContext, DumperOptions> dumperOptions) {
        this.dumperOptions = dumperOptions;
    }

    public Function<CamelContext, Resolver> getResolver() {
        return resolver;
    }

    /**
     * Resolver to detect implicit type
     */
    public void setResolver(Function<CamelContext, Resolver> resolver) {
        this.resolver = resolver;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Set a custom classloader
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
    }

    /**
     * Class of the object to be created
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
        addTypeFilters(TypeFilters.types(unmarshalType));
    }

    public List<TypeDescription> getTypeDescriptions() {
        return typeDescriptions;
    }

    /**
     * Make YAML aware how to parse a custom Class.
     */
    public void setTypeDescriptions(List<TypeDescription> typeDescriptions) {
        this.typeDescriptions = new CopyOnWriteArrayList<>(typeDescriptions);
    }

    public void addTypeDescriptions(Collection<TypeDescription> typeDescriptions) {
        if (this.typeDescriptions == null) {
            this.typeDescriptions = new CopyOnWriteArrayList<>();
        }

        this.typeDescriptions.addAll(typeDescriptions);
    }

    public void addTypeDescriptions(TypeDescription... typeDescriptions) {
        addTypeDescriptions(Arrays.asList(typeDescriptions));
    }

    public void addTypeDescription(Class<?> type, Tag tag) {
        if (this.typeDescriptions == null) {
            this.typeDescriptions = new CopyOnWriteArrayList<>();
        }

        this.typeDescriptions.add(new TypeDescription(type, tag));
    }

    public Map<Class<?>, Tag> getClassTags() {
        return classTags;
    }

    /**
     * Define a tag for the <code>Class</code> to serialize.
     */
    public void setClassTags(Map<Class<?>, Tag> classTags) {
        this.classTags = new ConcurrentHashMap<>();
        this.classTags.putAll(classTags);
    }

    public void addClassTags(Class<?> type, Tag tag) {
        if (this.classTags == null) {
            this.classTags = new ConcurrentHashMap<>();
        }

        this.classTags.put(type, tag);
    }

    public boolean isUseApplicationContextClassLoader() {
        return useApplicationContextClassLoader;
    }

    /**
     * Use ApplicationContextClassLoader as custom ClassLoader
     */
    public void setUseApplicationContextClassLoader(boolean useApplicationContextClassLoader) {
        this.useApplicationContextClassLoader = useApplicationContextClassLoader;
    }

    public boolean isPrettyFlow() {
        return prettyFlow;
    }

    /**
     * Force the emitter to produce a pretty YAML document when using the flow style.
     */
    public void setPrettyFlow(boolean prettyFlow) {
        this.prettyFlow = prettyFlow;
    }

    /**
     * Convenience method to set class tag for bot <code>Constructor</code> and <code>Representer</code>
     */
    public void addTag(Class<?> type, Tag tag) {
        addClassTags(type, tag);
        addTypeDescription(type, tag);
    }

    public List<TypeFilter> getTypeFilters() {
        return typeFilters;
    }

    /**
     * Set the types SnakeYAML is allowed to un-marshall
     */
    public void setTypeFilters(List<TypeFilter> typeFilters) {
        this.typeFilters = new CopyOnWriteArrayList<>(typeFilters);
    }

    public void setTypeFilterDefinitions(List<String> typeFilterDefinitions) {
        this.typeFilters = new CopyOnWriteArrayList<>();

        for (String definition : typeFilterDefinitions) {
            TypeFilters.valueOf(definition).ifPresent(this.typeFilters::add);
        }
    }

    public void addTypeFilters(Collection<TypeFilter> typeFilters) {
        if (this.typeFilters == null) {
            this.typeFilters = new CopyOnWriteArrayList<>();
        }

        this.typeFilters.addAll(typeFilters);
    }

    public void addTypeFilters(TypeFilter... typeFilters) {
        addTypeFilters(Arrays.asList(typeFilters));
    }

    public boolean isAllowAnyType() {
        return allowAnyType;
    }

    /**
     * Allow any class to be un-marshaled, same as setTypeFilters(TypeFilters.allowAll())
     */
    public void setAllowAnyType(boolean allowAnyType) {
        this.allowAnyType = allowAnyType;
    }

    public int getMaxAliasesForCollections() {
        return maxAliasesForCollections;
    }

    /**
     * Set the maximum amount of aliases allowed for collections.
     */
    public void setMaxAliasesForCollections(int maxAliasesForCollections) {
        this.maxAliasesForCollections = maxAliasesForCollections;
    }

    public boolean isAllowRecursiveKeys() {
        return allowRecursiveKeys;
    }

    /**
     * Set whether recursive keys are allowed.
     */
    public void setAllowRecursiveKeys(boolean allowRecursiveKeys) {
        this.allowRecursiveKeys = allowRecursiveKeys;
    }

    // ***************************
    // Defaults
    // ***************************

    private BaseConstructor defaultConstructor(CamelContext context) {
        Collection<TypeFilter> yamlTypeFilters = this.typeFilters;
        if (allowAnyType) {
            yamlTypeFilters = Collections.singletonList(TypeFilters.allowAll());
        }

        LoaderOptions options = new LoaderOptions();
        options.setTagInspector(new TrustedTagInspector());
        options.setAllowRecursiveKeys(allowRecursiveKeys);
        options.setMaxAliasesForCollections(maxAliasesForCollections);

        BaseConstructor yamlConstructor;
        if (yamlTypeFilters != null) {
            ClassLoader yamlClassLoader = this.classLoader;
            if (yamlClassLoader == null && useApplicationContextClassLoader) {
                yamlClassLoader = context.getApplicationContextClassLoader();
            }

            yamlConstructor = yamlClassLoader != null
                    ? typeFilterConstructor(yamlClassLoader, yamlTypeFilters, options)
                    : typeFilterConstructor(yamlTypeFilters, options);
        } else {
            yamlConstructor = new SafeConstructor(options);
        }

        if (typeDescriptions != null && yamlConstructor instanceof Constructor con) {
            for (TypeDescription typeDescription : typeDescriptions) {
                con.addTypeDescription(typeDescription);
            }
        }

        return yamlConstructor;
    }

    private Representer defaultRepresenter(CamelContext context) {
        Representer yamlRepresenter = new Representer(new DumperOptions());

        if (classTags != null) {
            for (Map.Entry<Class<?>, Tag> entry : classTags.entrySet()) {
                yamlRepresenter.addClassTag(entry.getKey(), entry.getValue());
            }
        }

        return yamlRepresenter;
    }

    private DumperOptions defaultDumperOptions(CamelContext context) {
        DumperOptions yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setPrettyFlow(prettyFlow);

        return yamlDumperOptions;
    }

    private Resolver defaultResolver(CamelContext context) {
        return new Resolver();
    }

    // ***************************
    // Constructors
    // ***************************

    private static Constructor typeFilterConstructor(final Collection<TypeFilter> typeFilters, LoaderOptions options) {
        return new Constructor(options) {
            @Override
            protected Class<?> getClassForName(String name) throws ClassNotFoundException {
                if (typeFilters.stream().noneMatch(f -> f.test(name))) {
                    throw new IllegalArgumentException("Type " + name + " is not allowed");
                }

                return super.getClassForName(name);
            }
        };
    }

    private static Constructor typeFilterConstructor(
            final ClassLoader classLoader, final Collection<TypeFilter> typeFilters,
            LoaderOptions options) {
        return new CustomClassLoaderConstructor(classLoader, options) {
            @Override
            protected Class<?> getClassForName(String name) throws ClassNotFoundException {
                if (typeFilters.stream().noneMatch(f -> f.test(name))) {
                    throw new IllegalArgumentException("Type " + name + " is not allowed");
                }

                return super.getClassForName(name);
            }
        };
    }

    final class TrustedTagInspector implements TagInspector {
        @Override
        public boolean isGlobalTagAllowed(Tag tag) {
            return true;
        }
    }
}
