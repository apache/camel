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
package org.apache.camel.dataformat.beanio;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.beanio.BeanReader;
import org.beanio.BeanReaderErrorHandler;
import org.beanio.StreamFactory;

import static org.apache.camel.dataformat.beanio.BeanIOHelper.getOrCreateBeanReaderErrorHandler;

/**
 * You can use {@link BeanIOSplitter} with the Camel Splitter EIP to split big payloads using a stream mode to avoid
 * reading the entire content into memory.
 */
public class BeanIOSplitter implements Expression {

    private BeanIOConfiguration configuration = new BeanIOConfiguration();
    private StreamFactory factory;

    public BeanIOSplitter() throws Exception {
    }

    public BeanIOSplitter(BeanIOConfiguration configuration) {
        this.configuration = configuration;
    }

    public BeanIOSplitter(String mapping, String streamName) {
        setMapping(mapping);
        setStreamName(streamName);
    }

    protected StreamFactory createStreamFactory(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(getStreamName(), "Stream name not configured.");
        // Create the stream factory that will be used to read/write objects.
        StreamFactory answer = StreamFactory.newInstance();

        // Load the mapping file using the resource helper to ensure it can be loaded in OSGi and other environments
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, getMapping());
        try {
            if (getProperties() != null) {
                answer.load(is, getProperties());
            } else {
                answer.load(is);
            }
        } finally {
            IOHelper.close(is);
        }

        return answer;
    }

    public Object evaluate(Exchange exchange) throws Exception {
        Message msg = exchange.getIn();
        Object body = msg.getBody();

        if (factory == null) {
            factory = createStreamFactory(exchange.getContext());
        }

        if (body instanceof WrappedFile wrappedfile) {
            body = wrappedfile.getFile();
        }
        Reader reader;
        if (body instanceof CharSequence text) {
            // text is read as it is, without a round trip through bytes (also a StringBuilder, which has no converter
            // to a Reader)
            reader = new StringReader(text.toString());
        } else if (body instanceof Reader) {
            reader = msg.getMandatoryBody(Reader.class);
        } else {
            // the bytes of a file or a stream are decoded with the charset of the exchange (such as the charset of the
            // file consumer), else with the encoding of this splitter
            String charsetName = ExchangeHelper.getCharsetName(exchange, false);
            Charset charset = charsetName != null ? Charset.forName(charsetName) : getEncoding();
            InputStream is = body instanceof File file
                    ? new FileInputStream(file) : msg.getMandatoryBody(InputStream.class);
            reader = IOHelper.buffered(new InputStreamReader(is, charset));
        }
        BeanReader beanReader;
        try {
            beanReader = factory.createReader(getStreamName(), reader);
        } catch (RuntimeException e) {
            IOHelper.close(reader);
            throw e;
        }

        BeanIOIterator iterator;
        try {
            // reads the first record, which can fail
            iterator = new BeanIOIterator(beanReader);
        } catch (RuntimeException e) {
            IOHelper.close(reader);
            throw e;
        }

        BeanReaderErrorHandler errorHandler = getOrCreateBeanReaderErrorHandler(configuration, exchange, null, iterator);
        beanReader.setErrorHandler(errorHandler);

        return iterator;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        try {
            Object result = evaluate(exchange);
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public BeanIOConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BeanIOConfiguration configuration) {
        this.configuration = configuration;
    }

    public StreamFactory getFactory() {
        return factory;
    }

    public void setFactory(StreamFactory factory) {
        this.factory = factory;
    }

    public String getMapping() {
        return configuration.getMapping();
    }

    public void setIgnoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
        configuration.setIgnoreUnexpectedRecords(ignoreUnexpectedRecords);
    }

    public void setProperties(Properties properties) {
        configuration.setProperties(properties);
    }

    public void setStreamName(String streamName) {
        configuration.setStreamName(streamName);
    }

    public boolean isIgnoreUnidentifiedRecords() {
        return configuration.isIgnoreUnidentifiedRecords();
    }

    public boolean isIgnoreInvalidRecords() {
        return configuration.isIgnoreInvalidRecords();
    }

    public void setIgnoreInvalidRecords(boolean ignoreInvalidRecords) {
        configuration.setIgnoreInvalidRecords(ignoreInvalidRecords);
    }

    public void setEncoding(Charset encoding) {
        configuration.setEncoding(encoding);
    }

    public boolean isIgnoreUnexpectedRecords() {
        return configuration.isIgnoreUnexpectedRecords();
    }

    public Properties getProperties() {
        return configuration.getProperties();
    }

    public String getStreamName() {
        return configuration.getStreamName();
    }

    public void setMapping(String mapping) {
        configuration.setMapping(mapping);
    }

    public void setIgnoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
        configuration.setIgnoreUnidentifiedRecords(ignoreUnidentifiedRecords);
    }

    public Charset getEncoding() {
        return configuration.getEncoding();
    }

    public BeanReaderErrorHandler getBeanReaderErrorHandler() {
        return configuration.getBeanReaderErrorHandler();
    }

    public void setBeanReaderErrorHandler(BeanReaderErrorHandler beanReaderErrorHandler) {
        configuration.setBeanReaderErrorHandler(beanReaderErrorHandler);
    }

    public String getBeanReaderErrorHandlerType() {
        return configuration.getBeanReaderErrorHandlerType();
    }

    public void setBeanReaderErrorHandlerType(String beanReaderErrorHandlerType) {
        configuration.setBeanReaderErrorHandlerType(beanReaderErrorHandlerType);
    }

    public void setBeanReaderErrorHandlerType(Class<?> beanReaderErrorHandlerType) {
        configuration.setBeanReaderErrorHandlerType(beanReaderErrorHandlerType);
    }
}
