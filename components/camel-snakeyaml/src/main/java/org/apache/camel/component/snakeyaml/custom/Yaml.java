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
/*
 * Copyright (c) 2008, http://www.snakeyaml.org
 */
package org.apache.camel.component.snakeyaml.custom;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.emitter.Emitable;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.parser.Parser;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.serializer.Serializer;

/**
 * Public YAML interface. This class is not thread-safe. Which means that all the methods of the same
 * instance can be called only by one thread.
 * It is better to create an instance for every YAML stream.
 *
 * NOTE - This is a slight port of the Yaml class in SnakeYaml to specify the maxAliasesForCollections
 * and also use CustomComposer + CustomConstructor. If this PR gets applied then we can remove it:
 * https://bitbucket.org/asomov/snakeyaml/pull-requests/55/allow-configuration-for-preventing-billion/diff
 */
public class Yaml {
    protected final Resolver resolver;
    protected BaseConstructor constructor;
    protected Representer representer;
    protected DumperOptions dumperOptions;
    protected LoaderOptions loadingConfig;
    private String name;
    private int maxAliasesForCollections = 50;


    /**
     * Create Yaml instance.
     */
    public Yaml() {
        this(new CustomConstructor(), new Representer(), new DumperOptions(), new LoaderOptions(),
                new Resolver());
    }

    /**
     * Create Yaml instance.
     *
     * @param dumperOptions DumperOptions to configure outgoing objects
     */
    public Yaml(DumperOptions dumperOptions) {
        this(new CustomConstructor(), new Representer(dumperOptions), dumperOptions);
    }

    /**
     * Create Yaml instance.
     *
     * @param loadingConfig LoadingConfig to control load behavior
     */
    public Yaml(LoaderOptions loadingConfig) {
        this(new CustomConstructor(), new Representer(), new DumperOptions(), loadingConfig);
    }

    /**
     * Create Yaml instance.
     *
     * @param representer Representer to emit outgoing objects
     */
    public Yaml(Representer representer) {
        this(new CustomConstructor(), representer);
    }

    /**
     * Create Yaml instance.
     *
     * @param constructor BaseConstructor to construct incoming documents
     */
    public Yaml(BaseConstructor constructor) {
        this(constructor, new Representer());
    }

    /**
     * Create Yaml instance.
     *
     * @param constructor BaseConstructor to construct incoming documents
     * @param representer Representer to emit outgoing objects
     */
    public Yaml(BaseConstructor constructor, Representer representer) {
        this(constructor, representer, initDumperOptions(representer));
    }

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     */
    public Yaml(Representer representer, DumperOptions dumperOptions) {
        this(new CustomConstructor(), representer, dumperOptions, new LoaderOptions(), new Resolver());
    }

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     */
    public Yaml(BaseConstructor constructor, Representer representer, DumperOptions dumperOptions) {
        this(constructor, representer, dumperOptions, new LoaderOptions(), new Resolver());
    }

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     * @param loadingConfig LoadingConfig to control load behavior
     */
    public Yaml(BaseConstructor constructor, Representer representer, DumperOptions dumperOptions,
                LoaderOptions loadingConfig) {
        this(constructor, representer, dumperOptions, loadingConfig, new Resolver());
    }

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     * @param resolver      Resolver to detect implicit type
     */
    public Yaml(BaseConstructor constructor, Representer representer, DumperOptions dumperOptions,
                Resolver resolver) {
        this(constructor, representer, dumperOptions, new LoaderOptions(), resolver);
    }

    public Yaml(BaseConstructor constructor, Representer representer, DumperOptions dumperOptions,
                Resolver resolver, int maxAliasesForCollections) {
        this(constructor, representer, dumperOptions, new LoaderOptions(), resolver);
        this.maxAliasesForCollections = maxAliasesForCollections;
    }

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     * @param loadingConfig LoadingConfig to control load behavior
     * @param resolver      Resolver to detect implicit type
     */
    public Yaml(BaseConstructor constructor, Representer representer, DumperOptions dumperOptions,
                LoaderOptions loadingConfig, Resolver resolver) {
        if (!constructor.isExplicitPropertyUtils()) {
            constructor.setPropertyUtils(representer.getPropertyUtils());
        } else if (!representer.isExplicitPropertyUtils()) {
            representer.setPropertyUtils(constructor.getPropertyUtils());
        }
        this.constructor = constructor;
        this.constructor.setAllowDuplicateKeys(loadingConfig.isAllowDuplicateKeys());
        this.constructor.setWrappedToRootException(loadingConfig.isWrappedToRootException());
        if (dumperOptions.getIndent() <= dumperOptions.getIndicatorIndent()) {
            throw new YAMLException("Indicator indent must be smaller then indent.");
        }
        representer.setDefaultFlowStyle(dumperOptions.getDefaultFlowStyle());
        representer.setDefaultScalarStyle(dumperOptions.getDefaultScalarStyle());
        representer.getPropertyUtils()
                .setAllowReadOnlyProperties(dumperOptions.isAllowReadOnlyProperties());
        representer.setTimeZone(dumperOptions.getTimeZone());
        this.representer = representer;
        this.dumperOptions = dumperOptions;
        this.loadingConfig = loadingConfig;
        this.resolver = resolver;
        this.name = "Yaml:" + System.identityHashCode(this);
    }

    private static DumperOptions initDumperOptions(Representer representer) {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(representer.getDefaultFlowStyle());
        dumperOptions.setDefaultScalarStyle(representer.getDefaultScalarStyle());
        dumperOptions.setAllowReadOnlyProperties(representer.getPropertyUtils().isAllowReadOnlyProperties());
        dumperOptions.setTimeZone(representer.getTimeZone());
        return dumperOptions;
    }

    /**
     * Serialize a Java object into a YAML String.
     *
     * @param data Java object to be Serialized to YAML
     * @return YAML String
     */
    public String dump(Object data) {
        List<Object> list = new ArrayList<Object>(1);
        list.add(data);
        return dumpAll(list.iterator());
    }

    /**
     * Produce the corresponding representation tree for a given Object.
     *
     * @param data instance to build the representation tree for
     * @return representation tree
     * @see <a href="http://yaml.org/spec/1.1/#id859333">Figure 3.1. Processing
     * Overview</a>
     */
    public Node represent(Object data) {
        return representer.represent(data);
    }

    /**
     * Serialize a sequence of Java objects into a YAML String.
     *
     * @param data Iterator with Objects
     * @return YAML String with all the objects in proper sequence
     */
    public String dumpAll(Iterator<? extends Object> data) {
        StringWriter buffer = new StringWriter();
        dumpAll(data, buffer, null);
        return buffer.toString();
    }

    /**
     * Serialize a Java object into a YAML stream.
     *
     * @param data   Java object to be serialized to YAML
     * @param output stream to write to
     */
    public void dump(Object data, Writer output) {
        List<Object> list = new ArrayList<Object>(1);
        list.add(data);
        dumpAll(list.iterator(), output, null);
    }

    /**
     * Serialize a sequence of Java objects into a YAML stream.
     *
     * @param data   Iterator with Objects
     * @param output stream to write to
     */
    public void dumpAll(Iterator<? extends Object> data, Writer output) {
        dumpAll(data, output, null);
    }

    private void dumpAll(Iterator<? extends Object> data, Writer output, Tag rootTag) {
        Serializer serializer = new Serializer(new Emitter(output, dumperOptions), resolver,
                dumperOptions, rootTag);
        try {
            serializer.open();
            while (data.hasNext()) {
                Node node = representer.represent(data.next());
                serializer.serialize(node);
            }
            serializer.close();
        } catch (IOException e) {
            throw new YAMLException(e);
        }
    }

    /**
     * <p>
     * Serialize a Java object into a YAML string. Override the default root tag
     * with <code>rootTag</code>.
     * </p>
     *
     * <p>
     * This method is similar to <code>Yaml.dump(data)</code> except that the
     * root tag for the whole document is replaced with the given tag. This has
     * two main uses.
     * </p>
     *
     * <p>
     * First, if the root tag is replaced with a standard YAML tag, such as
     * <code>Tag.MAP</code>, then the object will be dumped as a map. The root
     * tag will appear as <code>!!map</code>, or blank (implicit !!map).
     * </p>
     *
     * <p>
     * Second, if the root tag is replaced by a different custom tag, then the
     * document appears to be a different type when loaded. For example, if an
     * instance of MyClass is dumped with the tag !!YourClass, then it will be
     * handled as an instance of YourClass when loaded.
     * </p>
     *
     * @param data      Java object to be serialized to YAML
     * @param rootTag   the tag for the whole YAML document. The tag should be Tag.MAP
     *                  for a JavaBean to make the tag disappear (to use implicit tag
     *                  !!map). If <code>null</code> is provided then the standard tag
     *                  with the full class name is used.
     * @param flowStyle flow style for the whole document. See Chapter 10. Collection
     *                  Styles http://yaml.org/spec/1.1/#id930798. If
     *                  <code>null</code> is provided then the flow style from
     *                  DumperOptions is used.
     * @return YAML String
     */
    public String dumpAs(Object data, Tag rootTag, FlowStyle flowStyle) {
        FlowStyle oldStyle = representer.getDefaultFlowStyle();
        if (flowStyle != null) {
            representer.setDefaultFlowStyle(flowStyle);
        }
        List<Object> list = new ArrayList<Object>(1);
        list.add(data);
        StringWriter buffer = new StringWriter();
        dumpAll(list.iterator(), buffer, rootTag);
        representer.setDefaultFlowStyle(oldStyle);
        return buffer.toString();
    }

    /**
     * <p>
     * Serialize a Java object into a YAML string. Override the default root tag
     * with <code>Tag.MAP</code>.
     * </p>
     * <p>
     * This method is similar to <code>Yaml.dump(data)</code> except that the
     * root tag for the whole document is replaced with <code>Tag.MAP</code> tag
     * (implicit !!map).
     * </p>
     * <p>
     * Block Mapping is used as the collection style. See 10.2.2. Block Mappings
     * (http://yaml.org/spec/1.1/#id934537)
     * </p>
     *
     * @param data Java object to be serialized to YAML
     * @return YAML String
     */
    public String dumpAsMap(Object data) {
        return dumpAs(data, Tag.MAP, FlowStyle.BLOCK);
    }

    /**
     * Serialize the representation tree into Events.
     *
     * @param data representation tree
     * @return Event list
     * @see <a href="http://yaml.org/spec/1.1/#id859333">Processing Overview</a>
     */
    public List<Event> serialize(Node data) {
        SilentEmitter emitter = new SilentEmitter();
        Serializer serializer = new Serializer(emitter, resolver, dumperOptions, null);
        try {
            serializer.open();
            serializer.serialize(data);
            serializer.close();
        } catch (IOException e) {
            throw new YAMLException(e);
        }
        return emitter.getEvents();
    }

    private static class SilentEmitter implements Emitable {
        private List<Event> events = new ArrayList<Event>(100);

        public List<Event> getEvents() {
            return events;
        }

        @Override
        public void emit(Event event) throws IOException {
            events.add(event);
        }
    }

    /**
     * Parse the only YAML document in a String and produce the corresponding
     * Java object. (Because the encoding in known BOM is not respected.)
     *
     * @param yaml YAML data to load from (BOM must not be present)
     * @param <T>  the class of the instance to be created
     * @return parsed object
     */
    @SuppressWarnings("unchecked")
    public <T> T load(String yaml) {
        return (T) loadFromReader(new StreamReader(yaml), Object.class);
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param io  data to load from (BOM is respected to detect encoding and removed from the data)
     * @param <T> the class of the instance to be created
     * @return parsed object
     */
    @SuppressWarnings("unchecked")
    public <T> T load(InputStream io) {
        return (T) loadFromReader(new StreamReader(new UnicodeReader(io)), Object.class);
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param io  data to load from (BOM must not be present)
     * @param <T> the class of the instance to be created
     * @return parsed object
     */
    @SuppressWarnings("unchecked")
    public <T> T load(Reader io) {
        return (T) loadFromReader(new StreamReader(io), Object.class);
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param <T>  Class is defined by the second argument
     * @param io   data to load from (BOM must not be present)
     * @param type Class of the object to be created
     * @return parsed object
     */
    @SuppressWarnings("unchecked")
    public <T> T loadAs(Reader io, Class<T> type) {
        return (T) loadFromReader(new StreamReader(io), type);
    }

    /**
     * Parse the only YAML document in a String and produce the corresponding
     * Java object. (Because the encoding in known BOM is not respected.)
     *
     * @param <T>  Class is defined by the second argument
     * @param yaml YAML data to load from (BOM must not be present)
     * @param type Class of the object to be created
     * @return parsed object
     */
    @SuppressWarnings("unchecked")
    public <T> T loadAs(String yaml, Class<T> type) {
        return (T) loadFromReader(new StreamReader(yaml), type);
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param <T>   Class is defined by the second argument
     * @param input data to load from (BOM is respected to detect encoding and removed from the data)
     * @param type  Class of the object to be created
     * @return parsed object
     */
    @SuppressWarnings("unchecked")
    public <T> T loadAs(InputStream input, Class<T> type) {
        return (T) loadFromReader(new StreamReader(new UnicodeReader(input)), type);
    }

    private Object loadFromReader(StreamReader sreader, Class<?> type) {
        Composer composer = new CustomComposer(new ParserImpl(sreader), resolver, maxAliasesForCollections);
        constructor.setComposer(composer);
        return constructor.getSingleData(type);
    }

    /**
     * Parse all YAML documents in the Reader and produce corresponding Java
     * objects. The documents are parsed only when the iterator is invoked.
     *
     * @param yaml YAML data to load from (BOM must not be present)
     * @return an Iterable over the parsed Java objects in this String in proper
     * sequence
     */
    public Iterable<Object> loadAll(Reader yaml) {
        Composer composer = new CustomComposer(new ParserImpl(new StreamReader(yaml)), resolver, maxAliasesForCollections);
        constructor.setComposer(composer);
        Iterator<Object> result = new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return constructor.checkData();
            }

            @Override
            public Object next() {
                return constructor.getData();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return new YamlIterable(result);
    }

    private static class YamlIterable implements Iterable<Object> {
        private Iterator<Object> iterator;

        public YamlIterable(Iterator<Object> iterator) {
            this.iterator = iterator;
        }

        @Override
        public Iterator<Object> iterator() {
            return iterator;
        }
    }

    /**
     * Parse all YAML documents in a String and produce corresponding Java
     * objects. (Because the encoding in known BOM is not respected.) The
     * documents are parsed only when the iterator is invoked.
     *
     * @param yaml YAML data to load from (BOM must not be present)
     * @return an Iterable over the parsed Java objects in this String in proper
     * sequence
     */
    public Iterable<Object> loadAll(String yaml) {
        return loadAll(new StringReader(yaml));
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding Java
     * objects. The documents are parsed only when the iterator is invoked.
     *
     * @param yaml YAML data to load from (BOM is respected to detect encoding and removed from the data)
     * @return an Iterable over the parsed Java objects in this stream in proper
     * sequence
     */
    public Iterable<Object> loadAll(InputStream yaml) {
        return loadAll(new UnicodeReader(yaml));
    }

    /**
     * Parse the first YAML document in a stream and produce the corresponding
     * representation tree. (This is the opposite of the represent() method)
     *
     * @param yaml YAML document
     * @return parsed root Node for the specified YAML document
     * @see <a href="http://yaml.org/spec/1.1/#id859333">Figure 3.1. Processing
     * Overview</a>
     */
    public Node compose(Reader yaml) {
        Composer composer = new CustomComposer(new ParserImpl(new StreamReader(yaml)), resolver, maxAliasesForCollections);
        return composer.getSingleNode();
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding
     * representation trees.
     *
     * @param yaml stream of YAML documents
     * @return parsed root Nodes for all the specified YAML documents
     * @see <a href="http://yaml.org/spec/1.1/#id859333">Processing Overview</a>
     */
    public Iterable<Node> composeAll(Reader yaml) {
        final Composer composer = new CustomComposer(new ParserImpl(new StreamReader(yaml)), resolver, maxAliasesForCollections);
        Iterator<Node> result = new Iterator<Node>() {
            @Override
            public boolean hasNext() {
                return composer.checkNode();
            }

            @Override
            public Node next() {
                Node node = composer.getNode();
                if (node != null) {
                    return node;
                } else {
                    throw new NoSuchElementException("No Node is available.");
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return new NodeIterable(result);
    }

    private static class NodeIterable implements Iterable<Node> {
        private Iterator<Node> iterator;

        public NodeIterable(Iterator<Node> iterator) {
            this.iterator = iterator;
        }

        @Override
        public Iterator<Node> iterator() {
            return iterator;
        }
    }

    /**
     * Add an implicit scalar detector. If an implicit scalar value matches the
     * given regexp, the corresponding tag is assigned to the scalar.
     *
     * @param tag    tag to assign to the node
     * @param regexp regular expression to match against
     * @param first  a sequence of possible initial characters or null (which means
     *               any).
     */
    public void addImplicitResolver(Tag tag, Pattern regexp, String first) {
        resolver.addImplicitResolver(tag, regexp, first);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Get a meaningful name. It simplifies debugging in a multi-threaded
     * environment. If nothing is set explicitly the address of the instance is
     * returned.
     *
     * @return human readable name
     */
    public String getName() {
        return name;
    }

    /**
     * Set a meaningful name to be shown in toString()
     *
     * @param name human readable name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Parse a YAML stream and produce parsing events.
     *
     * @param yaml YAML document(s)
     * @return parsed events
     * @see <a href="http://yaml.org/spec/1.1/#id859333">Processing Overview</a>
     */
    public Iterable<Event> parse(Reader yaml) {
        final Parser parser = new ParserImpl(new StreamReader(yaml));
        Iterator<Event> result = new Iterator<Event>() {
            @Override
            public boolean hasNext() {
                return parser.peekEvent() != null;
            }

            @Override
            public Event next() {
                Event event = parser.getEvent();
                if (event != null) {
                    return event;
                } else {
                    throw new NoSuchElementException("No Event is available.");
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return new EventIterable(result);
    }

    private static class EventIterable implements Iterable<Event> {
        private Iterator<Event> iterator;

        public EventIterable(Iterator<Event> iterator) {
            this.iterator = iterator;
        }

        @Override
        public Iterator<Event> iterator() {
            return iterator;
        }
    }

    public void setBeanAccess(BeanAccess beanAccess) {
        constructor.getPropertyUtils().setBeanAccess(beanAccess);
        representer.getPropertyUtils().setBeanAccess(beanAccess);
    }

    public void addTypeDescription(TypeDescription td) {
        constructor.addTypeDescription(td);
        representer.addTypeDescription(td);
    }
}
