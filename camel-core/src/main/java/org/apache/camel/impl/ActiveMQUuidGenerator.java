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
package org.apache.camel.impl;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.InetAddressUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link org.apache.camel.spi.UuidGenerator} which is a fast implementation based on
 * how <a href="http://activemq.apache.org/>Apache ActiveMQ</a> generates its UUID.
 * <p/>
 * This implementation is not synchronized but it leverages API which may not be accessible
 * in the cloud (such as Google App Engine).
 */
public class ActiveMQUuidGenerator implements UuidGenerator {

    private static final transient Log LOG = LogFactory.getLog(ActiveMQUuidGenerator.class); 
    private static final String UNIQUE_STUB;
    private static int instanceCount;
    private static String hostName;
    private String seed;
    private final AtomicLong sequence = new AtomicLong(1);
    private final int length;

    static {
        String stub = "";
        boolean canAccessSystemProps = true;
        try {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPropertiesAccess();
            }
        } catch (SecurityException se) {
            canAccessSystemProps = false;
        }

        if (canAccessSystemProps) {
            try {
                hostName = InetAddressUtil.getLocalHostName();
                ServerSocket ss = new ServerSocket(0);
                stub = "-" + ss.getLocalPort() + "-" + System.currentTimeMillis() + "-";
                Thread.sleep(100);
                ss.close();
            } catch (Exception ioe) {
                LOG.warn("Cannot generate unique stub", ioe);
            }
        } else {
            hostName = "localhost";
            stub = "-1-" + System.currentTimeMillis() + "-";
        }
        UNIQUE_STUB = stub;
    }

    public ActiveMQUuidGenerator(String prefix) {
        synchronized (UNIQUE_STUB) {
            this.seed = prefix + UNIQUE_STUB + (instanceCount++) + "-";
            // let the ID be friendly for URL and file systems
            this.seed = generateSanitizedId(this.seed);
            this.length = seed.length() + ("" + Long.MAX_VALUE).length();
        }
    }

    public ActiveMQUuidGenerator() {
        this("ID-" + hostName);
    }

    /**
     * As we have to find the hostname as a side-affect of generating a unique
     * stub, we allow it's easy retrieval here
     * 
     * @return the local host name
     */
    public static String getHostName() {
        return hostName;
    }

    public String generateUuid() {
        StringBuilder sb = new StringBuilder(length);
        sb.append(seed);
        sb.append(sequence.getAndIncrement());
        return sb.toString();
    }

    /**
     * Generate a unique ID - that is friendly for a URL or file system
     * 
     * @return a unique id
     */
    public String generateSanitizedId() {
        return generateSanitizedId(generateUuid());
    }

    /**
     * Ensures that the id is friendly for a URL or file system
     *
     * @param id the unique id
     * @return the id as file friendly id
     */
    public static String generateSanitizedId(String id) {
        id = id.replace(':', '-');
        id = id.replace('_', '-');
        id = id.replace('.', '-');
        id = id.replace('/', '-');
        return id;
    }
}