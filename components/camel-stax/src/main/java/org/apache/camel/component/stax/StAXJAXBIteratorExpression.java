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
package org.apache.camel.component.stax;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.LRUSoftCache;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.stax.StAXUtil.getTagName;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} body
 * using an {@link Iterator}, which uses StAX to walk in streaming mode.
 * The elements returned is a POJO which is bound using JAXB annotations.
 * <p/>
 * The message body must be able to convert to {@link XMLEventReader} type which is used as stream
 * to access the message body. And there must be a JAXB annotated class to use as binding.
 */
public class StAXJAXBIteratorExpression<T> extends ExpressionAdapter {
    private static final Map<Class<?>, JAXBContext> JAX_CONTEXTS = new LRUSoftCache<Class<?>, JAXBContext>(1000);

    private final Class<T> handled;

    /**
     * Creates this expression.
     *
     * @param handled the class which has JAXB annotations to bind POJO.
     */
    public StAXJAXBIteratorExpression(Class<T> handled) {
        ObjectHelper.notNull(handled, "handled");
        this.handled = handled;
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
    public Object evaluate(Exchange exchange) {
        try {
            XMLEventReader reader = exchange.getIn().getMandatoryBody(XMLEventReader.class);
            return createIterator(reader, handled);
        } catch (InvalidPayloadException e) {
            exchange.setException(e);
            return null;
        } catch (JAXBException e) {
            exchange.setException(e);
            return null;
        }
    }

    private Iterator<T> createIterator(XMLEventReader reader, Class<T> clazz) throws JAXBException {
        return new StAXJAXBIterator<T>(clazz, reader);
    }

    /**
     * Iterator to walk the XML reader
     */
    static class StAXJAXBIterator<T> implements Iterator<T>, Closeable {

        private final XMLEventReader reader;
        private final Class<T> clazz;
        private final String name;
        private final Unmarshaller unmarshaller;
        private T element;

        StAXJAXBIterator(Class<T> clazz, XMLEventReader reader) throws JAXBException {
            this.clazz = clazz;
            this.reader = reader;

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

        @SuppressWarnings("unchecked")
        T getNextElement() {
            XMLEvent xmlEvent;
            boolean found = false;
            while (!found && reader.hasNext()) {
                try {
                    xmlEvent = reader.peek();
                    if (xmlEvent != null && xmlEvent.isStartElement() && name.equals(xmlEvent.asStartElement().getName().getLocalPart())) {
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

            Object answer;
            try {
                answer = unmarshaller.unmarshal(reader, clazz);
                if (answer != null && answer.getClass() == JAXBElement.class) {
                    JAXBElement jbe = (JAXBElement) answer;
                    answer = jbe.getValue();
                }
            } catch (JAXBException e) {
                throw new RuntimeCamelException(e);
            }

            return (T) answer;
        }

        @Override
        public void close() throws IOException {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }
    }

}
