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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
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
    private BaseConstructor constructor;
    private Representer representer;
    private DumperOptions dumperOptions;
    private Resolver resolver;
    private ClassLoader classLoader;
    private String unmarshalTypeName;
    private Class<?> unmarshalType;
    private boolean useApplicationContextClassLoader = true;
    private boolean prettyFlow;
    private boolean allowAnyType;
    private String typeFilters;
    private int maxAliasesForCollections = 50;
    private boolean allowRecursiveKeys;

    public SnakeYAMLDataFormat() {
        this(null);
    }

    public SnakeYAMLDataFormat(Class<?> type) {
        this.yamlCache = new ThreadLocal<>();
        this.unmarshalType = type;
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
            getYaml().dump(graph, osw);
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
            return getYaml().loadAs(s, unmarshalObjectType);
        } else if (body instanceof Reader r) {
            return getYaml().loadAs(r, unmarshalObjectType);
        } else {
            // fallback to InputStream
            InputStream is =
                    exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            Reader r = new InputStreamReader(is, ExchangeHelper.getCharsetName(exchange));
            return getYaml().loadAs(r, unmarshalObjectType);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (unmarshalTypeName != null && unmarshalType == null) {
            setUnmarshalType(camelContext.getClassResolver().resolveClass(unmarshalTypeName));
        }
        if (unmarshalType != null) {
            if (this.typeFilters == null) {
                this.typeFilters = unmarshalType.getName();
            } else {
                this.typeFilters += "," + unmarshalType.getName();
            }
        }
        if (allowAnyType) {
            typeFilters = "*";
        }
        if (this.constructor == null) {
            this.constructor = defaultConstructor(camelContext);
        }
        if (this.representer == null) {
            this.representer = defaultRepresenter();
        }
        if (this.dumperOptions == null) {
            this.dumperOptions = defaultDumperOptions();
        }
        if (this.resolver == null) {
            this.resolver = defaultResolver();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        yamlCache.remove();
    }

    private Yaml getYaml() {
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
            yaml = new Yaml(constructor, representer, dumperOptions, options, resolver);
            yamlCache.set(new WeakReference<>(yaml));
        }

        return yaml;
    }

    public BaseConstructor getConstructor() {
        return constructor;
    }

    public void setConstructor(BaseConstructor constructor) {
        this.constructor = constructor;
    }

    public Representer getRepresenter() {
        return representer;
    }

    public void setRepresenter(Representer representer) {
        this.representer = representer;
    }

    public DumperOptions getDumperOptions() {
        return dumperOptions;
    }

    public void setDumperOptions(DumperOptions dumperOptions) {
        this.dumperOptions = dumperOptions;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    public void setTypeFilters(String typeFilters) {
        this.typeFilters = typeFilters;
    }

    public void setTypeFilters(Class<?> typeFilters) {
        this.typeFilters = typeFilters.getName();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

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

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public boolean isUseApplicationContextClassLoader() {
        return useApplicationContextClassLoader;
    }

    public void setUseApplicationContextClassLoader(boolean useApplicationContextClassLoader) {
        this.useApplicationContextClassLoader = useApplicationContextClassLoader;
    }

    public boolean isPrettyFlow() {
        return prettyFlow;
    }

    public void setPrettyFlow(boolean prettyFlow) {
        this.prettyFlow = prettyFlow;
    }

    public boolean isAllowAnyType() {
        return allowAnyType;
    }

    public void setAllowAnyType(boolean allowAnyType) {
        this.allowAnyType = allowAnyType;
    }

    public int getMaxAliasesForCollections() {
        return maxAliasesForCollections;
    }

    public void setMaxAliasesForCollections(int maxAliasesForCollections) {
        this.maxAliasesForCollections = maxAliasesForCollections;
    }

    public boolean isAllowRecursiveKeys() {
        return allowRecursiveKeys;
    }

    public void setAllowRecursiveKeys(boolean allowRecursiveKeys) {
        this.allowRecursiveKeys = allowRecursiveKeys;
    }

    // ***************************
    // Defaults
    // ***************************

    private BaseConstructor defaultConstructor(CamelContext context) {
        LoaderOptions options = new LoaderOptions();
        options.setTagInspector(new TrustedTagInspector());
        options.setAllowRecursiveKeys(allowRecursiveKeys);
        options.setMaxAliasesForCollections(maxAliasesForCollections);

        BaseConstructor yamlConstructor;
        if (typeFilters != null) {
            ClassLoader yamlClassLoader = this.classLoader;
            if (yamlClassLoader == null && useApplicationContextClassLoader) {
                yamlClassLoader = context.getApplicationContextClassLoader();
            }
            yamlConstructor = yamlClassLoader != null
                    ? typeFilterConstructor(yamlClassLoader, options)
                    : typeFilterConstructor(options);
        } else {
            yamlConstructor = new SafeConstructor(options);
        }
        return yamlConstructor;
    }

    private Representer defaultRepresenter() {
        return new Representer(new DumperOptions());
    }

    private DumperOptions defaultDumperOptions() {
        DumperOptions yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setPrettyFlow(prettyFlow);
        return yamlDumperOptions;
    }

    private Resolver defaultResolver() {
        return new Resolver();
    }

    // ***************************
    // Constructors
    // ***************************

    private boolean allowTypeFilter(String className) {
        if (typeFilters == null) {
            return true;
        }
        return PatternHelper.matchPatterns(className, typeFilters.split(","));
    }

    private Constructor typeFilterConstructor(LoaderOptions options) {
        return new Constructor(options) {
            @Override
            protected Class<?> getClassForName(String name) throws ClassNotFoundException {
                if (!allowTypeFilter(name)) {
                    throw new IllegalArgumentException("Type " + name + " is not allowed");
                }
                return super.getClassForName(name);
            }
        };
    }

    private Constructor typeFilterConstructor(final ClassLoader classLoader, LoaderOptions options) {
        return new CustomClassLoaderConstructor(classLoader, options) {
            @Override
            protected Class<?> getClassForName(String name) throws ClassNotFoundException {
                if (!allowTypeFilter(name)) {
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
