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
package org.apache.camel.component.hawtdb;

import java.io.File;
import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fusesource.hawtdb.api.Index;
import org.fusesource.hawtdb.api.Transaction;
import org.fusesource.hawtdb.util.buffer.Buffer;
import org.fusesource.hawtdb.util.buffer.DataByteArrayInputStream;
import org.fusesource.hawtdb.util.buffer.DataByteArrayOutputStream;
import org.fusesource.hawtdb.util.marshaller.Marshaller;
import org.fusesource.hawtdb.util.marshaller.ObjectMarshaller;

/**
 * An instance of AggregationRepository which is backed by a HawtDB.
 */
public class HawtDBAggregationRepository<K> extends ServiceSupport implements AggregationRepository<K> {

    private static final transient Log LOG = LogFactory.getLog(HawtDBAggregationRepository.class);
    private HawtDBFile hawtDBFile;
    private String persistentFileName;
    private String repositoryName;
    private boolean sync;
    private boolean returnOldExchange;
    private Marshaller<K> keyMarshaller = new ObjectMarshaller<K>();
    private Marshaller<DefaultExchangeHolder> exchangeMarshaller = new ObjectMarshaller<DefaultExchangeHolder>();

    /**
     * Creates an aggregation repository
     */
    public HawtDBAggregationRepository() {
    }

    /**
     * Creates an aggregation repository
     *
     * @param repositoryName the repository name
     */
    public HawtDBAggregationRepository(String repositoryName) {
        ObjectHelper.notEmpty(repositoryName, "name");
        this.repositoryName = repositoryName;
    }

    /**
     * Creates an aggregation repository using a new {@link org.apache.camel.component.hawtdb.HawtDBFile}
     * that persists using the provided file.
     *
     * @param repositoryName the repository name
     * @param persistentFileName the persistent store filename
     */
    public HawtDBAggregationRepository(String repositoryName, String persistentFileName) {
        ObjectHelper.notEmpty(repositoryName, "name");
        ObjectHelper.notEmpty(persistentFileName, "fileName");
        this.hawtDBFile = new HawtDBFile();
        this.hawtDBFile.setFile(new File(persistentFileName));
        this.repositoryName = repositoryName;
    }

    /**
     * Creates an aggregation repository using the provided {@link org.apache.camel.component.hawtdb.HawtDBFile}.
     *
     * @param repositoryName the repository name
     * @param hawtDBFile the hawtdb file to use as persistent store
     */
    public HawtDBAggregationRepository(String repositoryName, HawtDBFile hawtDBFile) {
        ObjectHelper.notEmpty(repositoryName, "name");
        ObjectHelper.notNull(hawtDBFile, "HawtDBFile");
        this.hawtDBFile = hawtDBFile;
        this.repositoryName = repositoryName;
    }

    public Exchange add(CamelContext camelContext, final K key, Exchange exchange) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding key   [" + key + "] -> " + exchange);
        }
        try {
            // If we could guarantee that the key and exchange are immutable,
            // then we could have stuck them directly into the index, 
            // HawtDB could then eliminate the need to marshal and un-marshal  
            // in some cases.  But since we can.. we are going to force
            // early marshaling.
            final Buffer keyBuffer = marshallKey(key);
            final Buffer exchangeBuffer = marshallExchange(camelContext, exchange);
            Buffer rc = hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName);
                    return index.put(keyBuffer, exchangeBuffer);
                }

                @Override
                public String toString() {
                    return "Adding key [" + key + "]";
                }
            });
            if (rc == null) {
                return null;
            }

            // only return old exchange if enabled
            if (isReturnOldExchange()) {
                return unmarshallExchange(camelContext, rc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error adding to repository " + repositoryName + " with key " + key, e);
        }

        return null;
    }


    public Exchange get(CamelContext camelContext, final K key) {
        Exchange answer = null;
        try {
            final Buffer keyBuffer = marshallKey(key);
            Buffer rc = hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName);
                    return index.get(keyBuffer);
                }

                @Override
                public String toString() {
                    return "Getting key [" + key + "]";
                }
            });
            if (rc != null) {
                answer = unmarshallExchange(camelContext, rc);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error getting key " + key + " from repository " + repositoryName, e);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Getting key  [" + key + "] -> " + answer);
        }
        return answer;
    }

    public void remove(CamelContext camelContext, final K key) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Removing key [" + key + "]");
        }
        try {
            final Buffer keyBuffer = marshallKey(key);
            hawtDBFile.execute(new Work<Buffer>() {
                public Buffer execute(Transaction tx) {
                    Index<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, repositoryName);
                    return index.remove(keyBuffer);
                }

                @Override
                public String toString() {
                    return "Removing key [" + key + "]";
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error removing key " + key + " from repository " + repositoryName, e);
        }
    }

    protected Buffer marshallKey(K key) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        keyMarshaller.writePayload(key, baos);
        return baos.toBuffer();
    }

    protected Buffer marshallExchange(CamelContext camelContext, Exchange exchange) throws IOException {
        DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
        // use DefaultExchangeHolder to marshal to a serialized object
        DefaultExchangeHolder pe = DefaultExchangeHolder.marshal(exchange, false);
        // add the aggregated size property as the only property we want to retain
        DefaultExchangeHolder.addProperty(pe, Exchange.AGGREGATED_SIZE, exchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class));
        // persist the from endpoint as well
        if (exchange.getFromEndpoint() != null) {
            DefaultExchangeHolder.addProperty(pe, "CamelAggregatedFromEndpoint", exchange.getFromEndpoint().getEndpointUri());
        }
        exchangeMarshaller.writePayload(pe, baos);
        return baos.toBuffer();
    }

    protected Exchange unmarshallExchange(CamelContext camelContext, Buffer buffer) throws IOException {
        DataByteArrayInputStream bais = new DataByteArrayInputStream(buffer);
        DefaultExchangeHolder pe = exchangeMarshaller.readPayload(bais);
        Exchange answer = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(answer, pe);
        // restore the from endpoint
        String fromEndpointUri = (String) answer.removeProperty("CamelAggregatedFromEndpoint");
        if (fromEndpointUri != null) {
            Endpoint fromEndpoint = camelContext.hasEndpoint(fromEndpointUri);
            if (fromEndpoint != null) {
                answer.setFromEndpoint(fromEndpoint);
            }
        }
        return answer;
    }

    public HawtDBFile getHawtDBFile() {
        return hawtDBFile;
    }

    public void setHawtDBFile(HawtDBFile hawtDBFile) {
        this.hawtDBFile = hawtDBFile;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getPersistentFileName() {
        return persistentFileName;
    }

    public void setPersistentFileName(String persistentFileName) {
        this.persistentFileName = persistentFileName;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    public void setReturnOldExchange(boolean returnOldExchange) {
        this.returnOldExchange = returnOldExchange;
    }

    @Override
    protected void doStart() throws Exception {
        // either we have a HawtDB configured or we use a provided fileName
        if (hawtDBFile == null && persistentFileName != null) {
            hawtDBFile = new HawtDBFile();
            hawtDBFile.setFile(new File(persistentFileName));
            hawtDBFile.setSync(isSync());
        }

        ObjectHelper.notNull(hawtDBFile, "Either set a persistentFileName or a hawtDBFile");
        ObjectHelper.notNull(repositoryName, "repositoryName");

        ServiceHelper.startService(hawtDBFile);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(hawtDBFile);
    }

}
