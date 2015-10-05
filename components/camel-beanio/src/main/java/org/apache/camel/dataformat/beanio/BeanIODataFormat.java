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
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
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
public class BeanIODataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private static final String LOG_PREFIX = "BeanIO: ";
    private static final Logger LOG = LoggerFactory.getLogger(BeanIODataFormat.class);

    private transient CamelContext camelContext;
    private transient StreamFactory factory;
    private String streamName;
    private String mapping;
    private boolean ignoreUnidentifiedRecords;
    private boolean ignoreUnexpectedRecords;
    private boolean ignoreInvalidRecords;
    private Charset encoding = Charset.defaultCharset();
    private Properties properties;

    public BeanIODataFormat() {
    }

    public BeanIODataFormat(String mapping, String streamName) {
        this.mapping = mapping;
        this.streamName = streamName;
    }

    @Override
    public String getDataFormatName() {
        return "beanio";
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(streamName, "Stream name not configured.");
        if (factory == null) {
            // Create the stream factory that will be used to read/write objects.
            factory = StreamFactory.newInstance();

            // Load the mapping file using the resource helper to ensure it can be loaded in OSGi and other environments
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), mapping);
            try {
                if (properties != null) {
                    factory.load(is, properties);
                } else {
                    factory.load(is);
                }
            } finally {
                IOHelper.close(is);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        factory = null;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        List<Object> models = getModels(exchange, body);
        writeModels(stream, models);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return readModels(exchange, stream);
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
        BufferedWriter streamWriter = IOHelper.buffered(new OutputStreamWriter(stream, encoding));
        BeanWriter out = factory.createWriter(streamName, streamWriter);

        for (Object obj : models) {
            out.write(obj);
        }

        out.flush();
        out.close();
    }

    private List<Object> readModels(Exchange exchange, InputStream stream) {
        List<Object> results = new ArrayList<Object>();
        BufferedReader streamReader = IOHelper.buffered(new InputStreamReader(stream, encoding));

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
                String msg = LOG_PREFIX + "InvalidRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
                if (ignoreInvalidRecords) {
                    LOG.debug(msg);
                } else {
                    LOG.warn(msg);
                    throw ex;
                }
            }

            @Override
            public void unexpectedRecord(UnexpectedRecordException ex) throws Exception {
                String msg = LOG_PREFIX + "UnexpectedRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
                if (ignoreUnexpectedRecords) {
                    LOG.debug(msg);
                } else {
                    LOG.warn(msg);
                    throw ex;
                }
            }

            @Override
            public void unidentifiedRecord(UnidentifiedRecordException ex) throws Exception {
                String msg = LOG_PREFIX + "UnidentifiedRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
                if (ignoreUnidentifiedRecords) {
                    LOG.debug(msg);
                } else {
                    LOG.warn(msg);
                    throw ex;
                }
            }
        });
    }

    public Charset getEncoding() {
        return encoding;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }
    
    public void setEncoding(String encoding) {
        this.encoding = Charset.forName(encoding);
    }

    public boolean isIgnoreInvalidRecords() {
        return ignoreInvalidRecords;
    }

    public void setIgnoreInvalidRecords(boolean ignoreInvalidRecords) {
        this.ignoreInvalidRecords = ignoreInvalidRecords;
    }

    public boolean isIgnoreUnexpectedRecords() {
        return ignoreUnexpectedRecords;
    }

    public void setIgnoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
        this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
    }

    public boolean isIgnoreUnidentifiedRecords() {
        return ignoreUnidentifiedRecords;
    }

    public void setIgnoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
        this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
