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
package org.apache.camel.util;

import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.regex.PatternSyntaxException;
import java.net.URISyntaxException;
import java.net.URI;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Some helper methods for working with {@link Endpoint} instances
 *
 * @version $Revision$
 */
public final class EndpointHelper {

    private static final transient Log LOG = LogFactory.getLog(EndpointHelper.class);

    private EndpointHelper() {
        //Utility Class
    }
    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the endpoint
     * and invokes the given {@link Processor} to process each {@link Exchange} and then closes
     * down the consumer and throws any exceptions thrown.
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor, long timeout) throws Exception {
        PollingConsumer consumer = endpoint.createPollingConsumer();
        try {
            consumer.start();

            while (true) {
                Exchange exchange = consumer.receive(timeout);
                if (exchange == null) {
                    break;
                } else {
                    processor.process(exchange);
                }
            }
        } finally {
            try {
                consumer.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop PollingConsumer: " + e, e);
            }
        }
    }

    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the
     * endpoint and invokes the given {@link Processor} to process each
     * {@link Exchange} and then closes down the consumer and throws any
     * exceptions thrown.
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor) throws Exception {
        pollEndpoint(endpoint, processor, 1000L);
    }

    /**
     * Matches the endpoint with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     *   <li>excact match, returns true</li>
     *   <li>wildcard match (pattern ends with a * and the uri starts with the pattern), returns true</li>
     *   <li>regular expression match, returns true</li>
     *   <li>otherwise returns false</li>
     * </ul>
     *
     * @param uri  the endpoint uri
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    public static boolean matchEndpoint(String uri, String pattern) {
        // we need to test with and without scheme separators (//)
        if (uri.indexOf("://") != -1) {
            // try without :// also
            String scheme = ObjectHelper.before(uri, "://");
            String path = ObjectHelper.after(uri, "://");
            if (doMatchEndpoint(scheme + ":" + path, pattern)) {
                return true;
            }
        } else {
            // try with :// also
            String scheme = ObjectHelper.before(uri, ":");
            String path = ObjectHelper.after(uri, ":");
            if (doMatchEndpoint(scheme + "://" + path, pattern)) {
                return true;
            }
        }

        // and fallback to test with the uri as is
        return doMatchEndpoint(uri, pattern);
    }


    private static boolean doMatchEndpoint(String uri, String pattern) {
        if (uri.equals(pattern)) {
            // excact match
            return true;
        }

        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*") && uri.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }

        // match by regular expression
        try {
            if (uri.matches(pattern)) {
                return true;
            }
        } catch (PatternSyntaxException e) {
            // ignore
        }
        
        // no match
        return false;
    }

    /**
     * Sets the regular properties on the given bean
     *
     * @param context the camel context
     * @param bean the bean
     * @param parameters parameters
     * @throws Exception is thrown if setting property fails
     */
    public static void setProperties(CamelContext context, Object bean, Map parameters) throws Exception {
        IntrospectionSupport.setProperties(context.getTypeConverter(), bean, parameters);
    }

    /**
     * Sets the reference properties on the given bean
     * <p/>
     * This is convention over configuration, setting all reference parameters (using {@link #isReferenceParameter(String)}
     * by looking it up in registry and setting it on the bean if possible.
     *
     * @param context the camel context
     * @param bean the bean
     * @param parameters parameters
     * @throws Exception is thrown if setting property fails
     */
    public static void setReferenceProperties(CamelContext context, Object bean, Map parameters) throws Exception {
        Iterator it = parameters.keySet().iterator();
        while (it.hasNext()) {
            Object key = it.next();
            Object v = parameters.get(key);
            String value = v != null ? v.toString() : null;
            if (isReferenceParameter(value)) {
                Object ref = context.getRegistry().lookup(value.substring(1));
                String name = key.toString();
                if (ref != null) {
                    boolean hit = IntrospectionSupport.setProperty(context.getTypeConverter(), bean, name, ref);
                    if (hit) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Configued property: " + name + " on bean: " + bean + " with value: " + ref);
                        }
                        // must remove as its a valid option and we could configure it
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Is the given parameter a reference parameter (starting with a # char)
     *
     * @param parameter the parameter
     * @return <tt>true</tt> if its a reference parameter
     */
    public static boolean isReferenceParameter(String parameter) {
        return parameter != null && parameter.startsWith("#");
    }

}
