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
package org.apache.camel.component.kestrel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Represents the configuration of the Kestrel component and/or endpoint.
 */
@UriParams
public class KestrelConfiguration implements Cloneable {
    /**
     * The default port on which kestrel runs
     */
    public static final int DEFAULT_KESTREL_PORT = 22133;

    /**
     * The address(es) on which kestrel is running
     */
    @UriPath(defaultValue = "localhost:22133")
    private String[] addresses = new String[]{"localhost:" + DEFAULT_KESTREL_PORT};

    /**
     * How long a given wait should block (server side), in milliseconds
     */
    @UriParam(defaultValue = "100")
    private int waitTimeMs = 100;

    /**
     * How many concurrent listeners to schedule for the thread pool
     */
    @UriParam(defaultValue = "1")
    private int concurrentConsumers = 1;

    public String[] getAddresses() {
        return addresses;
    }

    /**
     * The addresses
     */
    public void setAddresses(String[] addresses) {
        this.addresses = addresses;
    }

    public int getWaitTimeMs() {
        return waitTimeMs;
    }

    /**
     * The wait time in milliseconds
     */
    public void setWaitTimeMs(int waitTimeMs) {
        this.waitTimeMs = waitTimeMs;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * The number of concurrent consumers
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        if (concurrentConsumers <= 0) {
            throw new IllegalArgumentException("Invalid value for concurrentConsumers: " + concurrentConsumers);
        }
        this.concurrentConsumers = concurrentConsumers;
    }

    public String getAddressesAsString() {
        StringBuilder bld = new StringBuilder();
        for (String address : addresses) {
            if (bld.length() > 0) {
                bld.append(',');
            }
            bld.append(address);
        }
        return bld.toString();
    }

    public List<InetSocketAddress> getInetSocketAddresses() {
        List<InetSocketAddress> list = new ArrayList<InetSocketAddress>();
        for (String address : addresses) {
            String[] tok = address.split(":");
            String host;
            int port;
            if (tok.length == 2) {
                host = tok[0];
                port = Integer.parseInt(tok[1]);
            } else if (tok.length == 1) {
                host = tok[0];
                port = DEFAULT_KESTREL_PORT;
            } else {
                throw new IllegalArgumentException("Invalid address: " + address);
            }
            list.add(new InetSocketAddress(host, port));
        }
        return list;
    }

    public KestrelConfiguration copy() {
        try {
            return (KestrelConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
