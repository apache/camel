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
package org.apache.camel.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Util class for {@link java.net.InetAddress}
 */
public final class InetAddressUtil {

    private InetAddressUtil() {
        // util class
    }

    /**
     * When using the {@link java.net.InetAddress#getHostName()} method in an
     * environment where neither a proper DNS lookup nor an <tt>/etc/hosts</tt>
     * entry exists for a given host, the following exception will be thrown:
     * <p/>
     * <code>
     * java.net.UnknownHostException: &lt;hostname&gt;: &lt;hostname&gt;
     * at java.net.InetAddress.getLocalHost(InetAddress.java:1425)
     * ...
     * </code>
     * <p/>
     * Instead of just throwing an UnknownHostException and giving up, this
     * method grabs a suitable hostname from the exception and prevents the
     * exception from being thrown. If a suitable hostname cannot be acquired
     * from the exception, only then is the <tt>UnknownHostException</tt> thrown.
     *
     * @return the hostname
     * @throws UnknownHostException is thrown if hostname could not be resolved
     */
    public static String getLocalHostName() throws UnknownHostException {
        try {
            return (InetAddress.getLocalHost()).getHostName();
        } catch (UnknownHostException uhe) {
            String host = uhe.getMessage(); // host = "hostname: hostname"
            if (host != null) {
                int colon = host.indexOf(':');
                if (colon > 0) {
                    return host.substring(0, colon);
                }
            }
            throw uhe;
        }
    }

    /**
     * When using the {@link java.net.InetAddress#getHostName()} method in an
     * environment where neither a proper DNS lookup nor an <tt>/etc/hosts</tt>
     * entry exists for a given host, the following exception will be thrown:
     * <p/>
     * <code>
     * java.net.UnknownHostException: &lt;hostname&gt;: &lt;hostname&gt;
     * at java.net.InetAddress.getLocalHost(InetAddress.java:1425)
     * ...
     * </code>
     * <p/>
     * Instead of just throwing an UnknownHostException and giving up, this
     * method grabs a suitable hostname from the exception and prevents the
     * exception from being thrown. If a suitable hostname cannot be acquired
     * from the exception, then <tt>null</tt> is returned
     *
     * @return the hostname, or <tt>null</tt> if not possible to resolve
     */
    public static String getLocalHostNameSafe() {
        try {
            return getLocalHostName();
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

}
