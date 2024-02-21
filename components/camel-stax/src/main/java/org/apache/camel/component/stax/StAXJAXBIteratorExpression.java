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
package org.apache.camel.component.stax;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.stax.StAXUtil.getTagName;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} body using an {@link Iterator}, which
 * uses StAX to walk in streaming mode. The elements returned is a POJO which is bound using JAXB annotations.
 * <p/>
 * The message body must be able to convert to {@link XMLEventReader} type which is used as stream to access the message
 * body. And there must be a JAXB annotated class to use as binding.
 */
public class StAXJAXBIteratorExpression<T> extends ExpressionAdapter {
    private static final Map<Class<?>, JAXBContext> JAX_CONTEXTS = LRUCacheFactory.newLRUSoftCache(1000);

    private final Class<T> handled;
    private final String handledName;
    private final boolean isNamespaceAware;

    /**
     * Creates this expression.
     *
     * @param handled the class which has JAXB annotations to bind POJO.
     */
    public StAXJAXBIteratorExpression(Class<T> handled) {
        this(handled, true);
    }

    /**
     * Creates this expression.
     *
     * @param handled          the class which has JAXB annotations to bind POJO.
     * @param isNamespaceAware sets the namespace awareness of the xml reader
     */
    public StAXJAXBIteratorExpression(Class<T> handled, boolean isNamespaceAware) {
        ObjectHelper.notNull(handled, "handled");
        this.handled = handled;
        this.handledName = null;
        this.isNamespaceAware = isNamespaceAware;
    }

    /**
     * Creates this expression.
     *
     * @param handledName the FQN name of the class which has JAXB annotations to bind POJO.
     */
    public StAXJAXBIteratorExpression(String handledName) {
        this(handledName, true);
    }

    /**
     * Creates this expression.
     *
     * @param handledName      the FQN name of the class which has JAXB annotations to bind POJO.
     * @param isNamespaceAware sets the namespace awareness of the xml reader
     */
    public StAXJAXBIteratorExpression(String handledName, boolean isNamespaceAware) {
        ObjectHelper.notNull(handledName, "handledName");
        this.handled = null;
        this.handledName = handledName;
        this.isNamespaceAware = isNamespaceAware;
    }

    private static JAXBContext jaxbContext(Class<?> handled) throws JAXBException {
        if (JAX_CONTEXTS.containsKey(handled)) {
            return JAX_CONTEXTS.get(handled);
        }

        JAXBContext context;
        synchronized (JAX_CONTEXTS) {
            context = JAXBContext.newInstance(handled);
            JAX_CONTEXTS.put(handled, context);
        }
        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object evaluate(Exchange exchange) {
        try {
            InputStream inputStream = null;
            XMLEventReader reader = exchange.getContext().getTypeConverter().tryConvertTo(XMLEventReader.class, exchange,
                    exchange.getIn().getBody());
            if (reader == null) {
                inputStream = exchange.getIn().getMandatoryBody(InputStream.class);
                XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
                xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, isNamespaceAware);
                xmlInputFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                xmlInputFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                reader = xmlInputFactory.createXMLEventReader(inputStream);
            }

            Class<T> clazz = handled;
            if (clazz == null && handledName != null) {
                clazz = (Class<T>) exchange.getContext().getClassResolver().resolveMandatoryClass(handledName);
            }
            return createIterator(reader, clazz, inputStream);
        } catch (InvalidPayloadException | JAXBException | ClassNotFoundException | XMLStreamException e) {
            exchange.setException(e);
            return null;
        }
    }

    private Iterator<T> createIterator(XMLEventReader reader, Class<T> clazz, InputStream inputStream) throws JAXBException {
        return new StAXJAXBIterator<>(clazz, reader, inputStream);
    }

    /**
     * Iterator to walk the XML reader
     */
    static class StAXJAXBIterator<T> implements Iterator<T>, Closeable {

        private final XMLEventReader reader;
        private final InputStream inputStream;
        private final Class<T> clazz;
        private final String name;
        private final Unmarshaller unmarshaller;
        private T element;

        StAXJAXBIterator(Class<T> clazz, XMLEventReader reader, InputStream inputStream) throws JAXBException {
            this.clazz = clazz;
            this.reader = reader;
            this.inputStream = inputStream;

            name = getTagName(clazz);
            JAXBContext jaxb = jaxbContext(clazz);
            // unmarshaller is not thread safe so we need to create a new instance per iterator
            unmarshaller = jaxb.createUnmarshaller();
        }

        @Override
        public boolean hasNext() {
            if (element == null) {
                element = getNextElement();
            }
            return element != null;
        }

        @Override
        public T next() {
            if (element == null) {
                element = getNextElement();
            }

            T answer = element;
            element = null;
            return answer;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        T getNextElement() {
            XMLEvent xmlEvent;
            boolean found = false;
            while (!found && reader.hasNext()) {
                try {
                    xmlEvent = reader.peek();
                    if (xmlEvent != null && xmlEvent.isStartElement()
                            && name.equals(xmlEvent.asStartElement().getName().getLocalPart())) {
                        found = true;
                    } else {
                        reader.nextEvent();
                    }
                } catch (XMLStreamException e) {
                    throw new RuntimeCamelException(e);
                }
            }

            if (!found) {
                return null;
            }

            try {
                return unmarshaller.unmarshal(reader, clazz).getValue();
            } catch (JAXBException e) {
                throw new RuntimeCamelException(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (inputStream != null) {
                IOHelper.close(inputStream);
            }
            try {
                reader.close();
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }
    }

}
