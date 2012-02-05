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
package org.apache.camel.dataformat.beanio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;
import org.beanio.BeanReader;
import org.beanio.BeanReaderErrorHandlerSupport;
import org.beanio.BeanWriter;
import org.beanio.InvalidRecordException;
import org.beanio.StreamFactory;
import org.beanio.UnexpectedRecordException;
import org.beanio.UnidentifiedRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) for beanio data.
 */
public class BeanIODataFormat implements DataFormat {

    private static final String LOG_PREFIX = "BeanIO: ";
    private static final transient Logger LOG = LoggerFactory.getLogger(BeanIODataFormat.class);

    private StreamFactory factory;
    private String streamName;
    private boolean ignoreUnidentifiedRecords;
    private boolean ignoreUnexpectedRecords;
    private boolean ignoreInvalidRecords;
    private Charset characterSet = Charset.defaultCharset();

    public BeanIODataFormat(String mapping, String streamName) {
        // Create the stream factory that will be used to read/write objects.
        factory = StreamFactory.newInstance();

        // Load the mapping file
        factory.loadResource(mapping);

        // Save the stream name that we want to read
        this.streamName = streamName;
    }

    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {

        validateRequiredProperties();

        List<Object> models = getModels(exchange, body);

        writeModels(stream, models);
    }

    private void validateRequiredProperties() {
        ObjectHelper.notNull(factory, "StreamFactory not configured.");
        ObjectHelper.notNull(streamName, "Stream name not configured.");
    }

    @SuppressWarnings("unchecked")
    private List<Object> getModels(Exchange exchange, Object body) {
        List<Object> models;
        if ((models = exchange.getContext().getTypeConverter().convertTo(List.class, body)) == null) {
            models = new ArrayList<Object>();
            Iterator<Object> it = ObjectHelper.createIterator(body);
            while (it.hasNext()) {
                models.add(it.next());
            }
        }
        return models;
    }

    private void writeModels(OutputStream stream, List<Object> models) {
        BufferedWriter streamWriter = new BufferedWriter(new OutputStreamWriter(stream, characterSet));
        BeanWriter out = factory.createWriter(streamName, streamWriter);

        for (Object obj : models) {
            out.write(obj);
        }

        out.flush();
        out.close();
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        validateRequiredProperties();
        return readModels(exchange, stream);
    }

    private List<Object> readModels(Exchange exchange, InputStream stream) {
        List<Object> results = new ArrayList<Object>();
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(stream, characterSet));

        BeanReader in = factory.createReader(streamName, streamReader);

        try {
            registerErrorHandler(in);

            Object readObject;
            while ((readObject = in.read()) != null) {
                if (readObject instanceof BeanIOHeader) {
                    exchange.getOut().getHeaders().putAll(((BeanIOHeader) readObject).getHeaders());
                }
                results.add(readObject);
            }
        } finally {
            in.close();
        }

        return results;
    }

    private void registerErrorHandler(BeanReader in) {
        in.setErrorHandler(new BeanReaderErrorHandlerSupport() {

            @Override
            public void invalidRecord(InvalidRecordException ex) throws Exception {
                LOG.warn(LOG_PREFIX + ex.getMessage() + ": " + ex.getContext().getRecordText());

                if (!ignoreInvalidRecords) {
                    throw ex;
                }
            }

            @Override
            public void unexpectedRecord(UnexpectedRecordException ex) throws Exception {
                LOG.warn(LOG_PREFIX + ex.getMessage() + ": " + ex.getContext().getRecordText());

                if (!ignoreUnexpectedRecords) {
                    throw ex;
                }
            }

            @Override
            public void unidentifiedRecord(UnidentifiedRecordException ex) throws Exception {
                LOG.warn(LOG_PREFIX + ex.getMessage() + ": " + ex.getContext().getRecordText());

                if (!ignoreUnidentifiedRecords) {
                    throw ex;
                }
            }
        });
    }

    /**
     * @param streamName The beanio stream that will be marshaled/unmarshaled.
     */
    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    /**
     * @param ignoreUnidentifiedRecords When true any unidentified records will be ignored when
     *                                  unmarshaling.
     */
    public void setIgnoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
        this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
    }

    /**
     * @param ignoreUnexpectedRecords When true any unexpected records will be ignored when
     *                                unmarshaling.
     */
    public void setIgnoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
        this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
    }

    /**
     * @param ignoreInvalidRecords When true any invalid records will be ignored when
     *                             unmarshaling.
     */
    public void setIgnoreInvalidRecords(boolean ignoreInvalidRecords) {
        this.ignoreInvalidRecords = ignoreInvalidRecords;
    }

    /**
     * @param characterSet the characterSet to set
     */
    public void setCharacterSet(String characterSet) {
        this.characterSet = Charset.forName(characterSet);
    }
}
