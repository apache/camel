/**
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
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;


/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using <a href="http://www.snakeyaml.org">SnakeYAML</a> to marshal to and from YAML.
 */
public class SnakeYAMLDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private ThreadLocal<WeakReference<Yaml>> yamlCache;
    private BaseConstructor constructor;
    private Representer representer;
    private DumperOptions dumperOptions;
    private Resolver resolver;
    private ClassLoader classLoader;
    private Class<?> unmarshalType;
    private List<TypeDescription> typeDescriptions;
    private Map<Class<?>, Tag> classTags;
    private boolean useApplicationContextClassLoader;
    private boolean prettyFlow;

    public SnakeYAMLDataFormat() {
        this(Object.class);
    }

    public SnakeYAMLDataFormat(Class<?> type) {
        this.unmarshalType = type;
        this.yamlCache = new ThreadLocal<>();
        this.useApplicationContextClassLoader = true;
        this.prettyFlow = false;
    }

    @Override
    public String getDataFormatName() {
        return "yaml-snakeyaml";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        try (final OutputStreamWriter osw = new OutputStreamWriter(stream, IOHelper.getCharsetName(exchange))) {
            getYaml(exchange.getContext()).dump(graph, osw);
        }
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        try (final InputStreamReader isr = new InputStreamReader(stream, IOHelper.getCharsetName(exchange))) {
            return getYaml(exchange.getContext()).loadAs(isr, unmarshalType);
        }
    }

    protected Yaml getYaml(CamelContext context) {
        Yaml yaml = null;
        WeakReference<Yaml> ref = yamlCache.get();

        if (ref != null) {
            yaml = ref.get();
        }

        if (yaml == null) {
            BaseConstructor yamlConstructor = this.constructor;
            Representer yamlRepresenter = this.representer;
            DumperOptions yamlDumperOptions = this.dumperOptions;
            Resolver yamlResolver = this.resolver;
            ClassLoader yamlClassLoader = this.classLoader;

            if (yamlClassLoader == null && useApplicationContextClassLoader) {
                yamlClassLoader = context.getApplicationContextClassLoader();
            }

            if (yamlConstructor == null) {
                yamlConstructor = yamlClassLoader == null
                    ? new Constructor()
                    : new CustomClassLoaderConstructor(yamlClassLoader);

                if (typeDescriptions != null) {
                    for (TypeDescription typeDescription : typeDescriptions) {
                        ((Constructor)yamlConstructor).addTypeDescription(typeDescription);
                    }
                }
            }
            if (yamlRepresenter == null) {
                yamlRepresenter = new Representer();

                if (classTags != null) {
                    for (Map.Entry<Class<?>, Tag> entry : classTags.entrySet()) {
                        yamlRepresenter.addClassTag(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (yamlDumperOptions == null) {
                yamlDumperOptions = new DumperOptions();
                yamlDumperOptions.setPrettyFlow(prettyFlow);
            }
            if (yamlResolver == null) {
                yamlResolver = new Resolver();
            }

            yaml = new Yaml(yamlConstructor, yamlRepresenter, yamlDumperOptions, yamlResolver);
            yamlCache.set(new WeakReference<>(yaml));
        }

        return yaml;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public BaseConstructor getConstructor() {
        return constructor;
    }

    /**
     * BaseConstructor to construct incoming documents.
     */
    public void setConstructor(BaseConstructor constructor) {
        this.constructor = constructor;
    }

    public Representer getRepresenter() {
        return representer;
    }

    /**
     * Representer to emit outgoing objects.
     */
    public void setRepresenter(Representer representer) {
        this.representer = representer;
    }

    public DumperOptions getDumperOptions() {
        return dumperOptions;
    }

    /**
     * DumperOptions to configure outgoing objects.
     */
    public void setDumperOptions(DumperOptions dumperOptions) {
        this.dumperOptions = dumperOptions;
    }

    public Resolver getResolver() {
        return resolver;
    }

    /**
     * Resolver to detect implicit type
     */
    public void setResolver(Resolver resolver) {
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

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
    }

    /**
     * Class of the object to be created
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public List<TypeDescription> getTypeDescriptions() {
        return typeDescriptions;
    }

    /**
     * Make YAML aware how to parse a custom Class.
     */
    public void setTypeDescriptions(List<TypeDescription> typeDescriptions) {
        this.typeDescriptions = typeDescriptions;
    }

    public void addTypeDescriptions(TypeDescription... typeDescriptions) {
        if (this.typeDescriptions == null) {
            this.typeDescriptions = new LinkedList<>();
        }

        for (TypeDescription typeDescription : typeDescriptions) {
            this.typeDescriptions.add(typeDescription);
        }
    }

    public void addTypeDescription(Class<?> type, Tag tag) {
        if (this.typeDescriptions == null) {
            this.typeDescriptions = new LinkedList<>();
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
        this.classTags = classTags;
    }

    public void addClassTags(Class<?> type, Tag tag) {
        if (this.classTags == null) {
            this.classTags = new LinkedHashMap<>();
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
     * Force the emitter to produce a pretty YAML document when using the flow
     * style.
     */
    public void setPrettyFlow(boolean prettyFlow) {
        this.prettyFlow = prettyFlow;
    }

    /**
     * Convenience method to set class tag for bot <code>Constructor</code> and
     * <code>Representer</code>
     */
    public void addTag(Class<?> type, Tag tag) {
        addClassTags(type, tag);
        addTypeDescription(type, tag);
    }
}
