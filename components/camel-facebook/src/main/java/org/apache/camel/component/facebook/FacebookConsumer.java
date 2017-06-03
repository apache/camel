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
package org.apache.camel.component.facebook;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import facebook4j.Facebook;
import facebook4j.Reading;
import facebook4j.json.DataObjectFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.facebook.data.FacebookMethodsType;
import org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.MatchType;
import org.apache.camel.component.facebook.data.FacebookPropertiesHelper;
import org.apache.camel.component.facebook.data.ReadingBuilder;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.camel.component.facebook.FacebookConstants.FACEBOOK_DATE_FORMAT;
import static org.apache.camel.component.facebook.FacebookConstants.READING_PREFIX;
import static org.apache.camel.component.facebook.FacebookConstants.READING_PROPERTY;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.filterMethods;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getHighestPriorityMethod;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.getMissingProperties;
import static org.apache.camel.component.facebook.data.FacebookMethodsTypeHelper.invokeMethod;

/**
 * The Facebook consumer.
 */
public class FacebookConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookConsumer.class);
    private static final String SINCE_PREFIX = "since=";

    private final FacebookEndpoint endpoint;
    private final FacebookMethodsType method;
    private final Map<String, Object> endpointProperties;

    private String sinceTime;
    private String untilTime;

    public FacebookConsumer(FacebookEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;

        // determine the consumer method to invoke
        this.method = findMethod();

        // get endpoint properties in a map
        final HashMap<String, Object> properties = new HashMap<String, Object>();
        FacebookPropertiesHelper.getEndpointProperties(endpoint.getConfiguration(), properties);

        // skip since and until fields?
        final Reading reading = (Reading) properties.get(READING_PROPERTY);
        if (reading != null) {
            final String queryString = reading.toString();
            if (queryString.contains(SINCE_PREFIX)) {
                // use the user supplied value to start with
                final int startIndex = queryString.indexOf(SINCE_PREFIX) + SINCE_PREFIX.length();
                int endIndex = queryString.indexOf('&', startIndex);
                if (endIndex == -1) {
                    // ignore the closing square bracket
                    endIndex = queryString.length() - 1;
                }
                final String strSince = queryString.substring(startIndex, endIndex);
                try {
                    this.sinceTime = URLDecoder.decode(strSince, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeCamelException(String.format("Error decoding %s.since with value %s due to: %s", READING_PREFIX, strSince, e.getMessage()), e);
                }
                LOG.debug("Using supplied property {}since value {}", READING_PREFIX, this.sinceTime);
            }
            if (queryString.contains("until=")) {
                LOG.debug("Overriding configured property {}until", READING_PREFIX);
            }
        }
        this.endpointProperties = Collections.unmodifiableMap(properties);
    }

    @Override
    public boolean isGreedy() {
        // make this consumer not greedy to avoid making too many Facebook calls
        return false;
    }

    private FacebookMethodsType findMethod() {

        FacebookMethodsType result;
        // find one that takes the largest subset of endpoint parameters
        final Set<String> argNames = new HashSet<String>();
        argNames.addAll(FacebookPropertiesHelper.getEndpointPropertyNames(endpoint.getConfiguration()));

        // add reading property for polling, if it doesn't already exist!
        argNames.add(READING_PROPERTY);

        final String[] argNamesArray = argNames.toArray(new String[argNames.size()]);
        List<FacebookMethodsType> filteredMethods = filterMethods(
            endpoint.getCandidates(), MatchType.SUPER_SET, argNamesArray);

        if (filteredMethods.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Missing properties for %s, need one or more from %s",
                    endpoint.getMethod(),
                    getMissingProperties(endpoint.getMethod(), endpoint.getNameStyle(), argNames)));
        } else if (filteredMethods.size() == 1) {
            // single match
            result = filteredMethods.get(0);
        } else {
            result = getHighestPriorityMethod(filteredMethods);
            LOG.warn("Using highest priority method {} from methods {}", method, filteredMethods);
        }
        return result;
    }

    @Override
    protected int poll() throws Exception {
        // invoke the consumer method
        final Map<String, Object> args = getMethodArguments();
        try {
            // also check whether we need to get raw JSON
            String rawJSON = null;
            Object result;
            if (endpoint.getConfiguration().getJsonStoreEnabled() == null
                || !endpoint.getConfiguration().getJsonStoreEnabled()) {
                result = invokeMethod(endpoint.getConfiguration().getFacebook(),
                    method, args);
            } else {
                final Facebook facebook = endpoint.getConfiguration().getFacebook();
                synchronized (facebook) {
                    result = invokeMethod(facebook, method, args);
                    rawJSON = DataObjectFactory.getRawJSON(result);
                }
            }

            // process result according to type
            if (result != null && (result instanceof Collection || result.getClass().isArray())) {
                // create an exchange for every element
                final Object array = getResultAsArray(result);
                final int length = Array.getLength(array);
                for (int i = 0; i < length; i++) {
                    processResult(Array.get(array, i), rawJSON);
                }
                return length;
            } else {
                processResult(result, rawJSON);
                return 1; // number of messages polled
            }
        } catch (Throwable t) {
            throw ObjectHelper.wrapRuntimeCamelException(t);
        }
    }

    private void processResult(Object result, String rawJSON) throws Exception {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(result);
        if (rawJSON != null) {
            exchange.getIn().setHeader(FacebookConstants.RAW_JSON_HEADER, rawJSON);
        }
        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }

    private Object getResultAsArray(Object result) {
        if (result.getClass().isArray()) {
            // no conversion needed
            return result;
        }
        // must be a Collection
        // TODO add support for Paging using ResponseList
        Collection<?> collection = (Collection<?>) result;
        return collection.toArray(new Object[collection.size()]);
    }

    private Map<String, Object> getMethodArguments() {
        // start by setting the Reading since and until fields,
        // these are used to avoid reading duplicate results across polls
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.putAll(endpointProperties);

        Reading reading = (Reading) arguments.remove(READING_PROPERTY);
        if (reading == null) {
            reading = new Reading();
        } else {
            try {
                reading = ReadingBuilder.copy(reading, true);
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(String.format("Error creating property [%s]: %s",
                        READING_PROPERTY, e.getMessage()), e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(String.format("Error creating property [%s]: %s",
                        READING_PROPERTY, e.getMessage()), e);
            }
        }

        // now set since and until for this poll
        final SimpleDateFormat dateFormat = new SimpleDateFormat(FACEBOOK_DATE_FORMAT);
        final long currentMillis = System.currentTimeMillis();
        if (this.sinceTime == null) {
            // first poll, set this to (current time - initial poll delay)
            final Date startTime = new Date(currentMillis
                - TimeUnit.MILLISECONDS.convert(getInitialDelay(), getTimeUnit()));
            this.sinceTime = dateFormat.format(startTime);
        } else if (this.untilTime != null) {
            // use the last 'until' time
            this.sinceTime = this.untilTime;
        }
        this.untilTime = dateFormat.format(new Date(currentMillis));

        reading.since(this.sinceTime);
        reading.until(this.untilTime);

        arguments.put(READING_PROPERTY, reading);

        return arguments;
    }

}
