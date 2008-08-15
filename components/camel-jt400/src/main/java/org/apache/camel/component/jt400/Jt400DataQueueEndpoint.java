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
package org.apache.camel.component.jt400;

import java.beans.PropertyVetoException;
import java.net.URI;
import java.net.URISyntaxException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.DataQueue;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;

/**
 * AS/400 Data queue endpoint
 */
public class Jt400DataQueueEndpoint extends DefaultPollingEndpoint<Exchange> {

    /**
     * Enumeration of supported data formats
     */
    public enum Format {

        /**
         * Using <code>String</code> for transferring data
         */
        text,

        /**
         * Using <code>byte[]</code> for transferring data
         */
        binary;
    }

    private final AS400 system;
    private final String objectPath;
    private DataQueue dataqueue;
    private Format format = Format.text;

    /**
     * Creates a new AS/400 data queue endpoint
     */
    protected Jt400DataQueueEndpoint(String endpointUri, Jt400Component component) throws CamelException {
        super(endpointUri, component);
        try {
            URI uri = new URI(endpointUri);
            String[] credentials = uri.getUserInfo().split(":");
            system = new AS400(uri.getHost(), credentials[0], credentials[1]);
            objectPath = uri.getPath();
        } catch (URISyntaxException e) {
            throw new CamelException("Unable to parse URI for " + endpointUri, e);
        }
    }

    public void setCcsid(int ccsid) throws PropertyVetoException {
        this.system.setCcsid(ccsid);
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public Format getFormat() {
        return format;
    }

    @Override
    public PollingConsumer<Exchange> createPollingConsumer() throws Exception {
        return new Jt400DataQueueConsumer(this);
    }

    public Producer<Exchange> createProducer() throws Exception {
        return new Jt400DataQueueProducer(this);
    }

    protected AS400 getSystem() {
        return system;
    }

    protected DataQueue getDataQueue() {
        if (dataqueue == null) {
            dataqueue = new DataQueue(system, objectPath);
        }
        return dataqueue;
    }

    public boolean isSingleton() {
        return false;
    }

}
