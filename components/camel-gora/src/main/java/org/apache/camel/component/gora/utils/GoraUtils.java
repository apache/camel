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
package org.apache.camel.component.gora.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.gora.GoraAttribute;
import org.apache.camel.component.gora.GoraConfiguration;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.query.Query;
import org.apache.gora.store.DataStore;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * GoraUtil class contain utility methods for the
 * camel component.
 */
public final class GoraUtils {
    
    /**
     * Private Constructor to prevent
     * instantiation of the class.
     */
    private GoraUtils() {
        // utility Class
    }

    /**
     * Utility method to construct a new query from the exchange
     *
     * <b>NOTE:</b> values used in order construct the query
     * should be stored in the "in" message headers.
     */
    public static Query<Object, Persistent> constractQueryFromConfiguration(final DataStore<Object, Persistent> dataStore, final GoraConfiguration conf)
        throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {

        final Query<Object, Persistent> query = dataStore.newQuery();

        if (configurationExist(GoraAttribute.GORA_QUERY_START_TIME, conf)) {
            query.setStartTime(getAttributeAsLong(GoraAttribute.GORA_QUERY_START_TIME, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_END_TIME, conf)) {
            query.setEndTime(getAttributeAsLong(GoraAttribute.GORA_QUERY_END_TIME, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_LIMIT, conf)) {
            query.setLimit(getAttributeAsLong(GoraAttribute.GORA_QUERY_LIMIT, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_TIME_RANGE_FROM, conf)
                && configurationExist(GoraAttribute.GORA_QUERY_TIME_RANGE_TO, conf)) {
            query.setTimeRange(getAttributeAsLong(GoraAttribute.GORA_QUERY_TIME_RANGE_FROM, conf),
                    getAttributeAsLong(GoraAttribute.GORA_QUERY_TIME_RANGE_TO, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_TIMESTAMP, conf)) {
            query.setTimestamp(getAttributeAsLong(GoraAttribute.GORA_QUERY_TIMESTAMP, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_START_KEY, conf)) {
            query.setStartKey(getAttribute(GoraAttribute.GORA_QUERY_START_KEY, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_END_KEY, conf)) {
            query.setEndKey(getAttribute(GoraAttribute.GORA_QUERY_END_KEY, conf));
        }

        if (configurationExist(GoraAttribute.GORA_QUERY_KEY_RANGE_FROM, conf)
                && configurationExist(GoraAttribute.GORA_QUERY_KEY_RANGE_TO, conf)) {
            query.setKeyRange(getAttribute(GoraAttribute.GORA_QUERY_KEY_RANGE_FROM, conf),
                    getAttribute(GoraAttribute.GORA_QUERY_KEY_RANGE_TO, conf));
        }

        return query;
    }

    /**
     * Utility method to construct a new query from the exchange
     *
     * <b>NOTE:</b> values used in order construct the query
     * should be stored in the "in" message headers.
     */
    public static Query<Object, Persistent> constractQueryFromPropertiesMap(final Map<String, ?> propertiesMap,
                                                                                 final DataStore<Object, Persistent> dataStore,
                                                                                 final GoraConfiguration conf) throws ClassNotFoundException {

        final Query<Object, Persistent> query = dataStore.newQuery();

        if (propertyExist(GoraAttribute.GORA_QUERY_START_TIME, propertiesMap)) {
            query.setStartTime(getPropertyAsLong(GoraAttribute.GORA_QUERY_START_TIME, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_END_TIME, propertiesMap)) {
            query.setEndTime(getPropertyAsLong(GoraAttribute.GORA_QUERY_END_TIME, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_LIMIT, propertiesMap)) {
            query.setLimit(getPropertyAsLong(GoraAttribute.GORA_QUERY_LIMIT, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_TIME_RANGE_FROM, propertiesMap)
                && propertyExist(GoraAttribute.GORA_QUERY_TIME_RANGE_TO, propertiesMap)) {
            query.setTimeRange(getPropertyAsLong(GoraAttribute.GORA_QUERY_TIME_RANGE_FROM, propertiesMap),
                               getPropertyAsLong(GoraAttribute.GORA_QUERY_TIME_RANGE_TO, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_TIMESTAMP, propertiesMap)) {
            query.setTimestamp(getPropertyAsLong(GoraAttribute.GORA_QUERY_TIMESTAMP, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_START_KEY, propertiesMap)) {
            query.setStartKey(getProperty(GoraAttribute.GORA_QUERY_START_KEY, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_END_KEY, propertiesMap)) {
            query.setStartKey(getProperty(GoraAttribute.GORA_QUERY_END_KEY, propertiesMap));
        }

        if (propertyExist(GoraAttribute.GORA_QUERY_KEY_RANGE_FROM, propertiesMap)
                && propertyExist(GoraAttribute.GORA_QUERY_KEY_RANGE_TO, propertiesMap)) {
            query.setKeyRange(getProperty(GoraAttribute.GORA_QUERY_KEY_RANGE_FROM, propertiesMap),
                    getProperty(GoraAttribute.GORA_QUERY_KEY_RANGE_TO, propertiesMap));
        }

        return query;
    }

    /**
     * Utility method to check if a value exist in the configuration class
     *
     * <b>NOTE:</>
     * Checks only if is not null
     */
    protected static boolean configurationExist(final GoraAttribute attr,
                                                final GoraConfiguration conf) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return PropertyUtils.getSimpleProperty(conf, attr.value) != null;
    }

    /**
     * Utility method to check if a value exist in the properties map
     */
    protected static boolean propertyExist(final GoraAttribute attr,
                                           final Map<String, ?> propertiesMap) {
        return propertiesMap.containsKey(attr.value);
    }


    /**
     * Utility method to extract value from configuration
     */
    protected static Object getAttribute(final GoraAttribute attr,
                                         final GoraConfiguration conf) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return PropertyUtils.getSimpleProperty(conf, attr.value);
    }

    /**
     * Utility method to extract value from configuration as String
     */
    protected static String getAttributeAsString(final GoraAttribute attr,
                                                 final GoraConfiguration conf) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return String.valueOf(getAttribute(attr, conf));
    }


    /**
     * Utility method to extract value from configuration as Long
     */
    protected static Long getAttributeAsLong(final GoraAttribute attr,
                                           final GoraConfiguration conf) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return Long.parseLong(getAttributeAsString(attr, conf));
    }

    /**
     * Utility method to extract value of a map
     */
    protected static Object getProperty(final GoraAttribute attr,
                                        final Map<String, ?> propertiesMap) {
        return propertiesMap.get(attr.value);
    }

    /**
     * Utility method to extract value of a map as String
     */
    protected static String getPropertyAsString(final GoraAttribute attr,
                                              final Map<String, ?> propertiesMap) {
        return String.valueOf(getProperty(attr, propertiesMap));
    }

    /**
     * Utility method to extract value of a map as long
     */
    protected static Long getPropertyAsLong(final GoraAttribute attr,
                                          final Map<String, ?> propertiesMap) {
        return Long.parseLong(getPropertyAsString(attr, propertiesMap));
    }


    /**
     * Utility method to extract GORA key from the exchange
     *
     * <b>NOTE:</b> key value expected to be stored
     * in the "in" message headers.
     *
     * @param exchange The Camel Exchange
     * @return The key
     */
    public static Object getKeyFromExchange(Exchange exchange) {
        final Object key = exchange.getIn().getHeader(GoraAttribute.GORA_KEY.value);
        checkNotNull(key, "Key should not be null!");
        return key;
    }

    /**
     * Utility method to extract the value from the exchange
     *
     * <b>NOTE:</b> the value expected to be instance
     * of persistent type.
     *
     * @param exchange The Camel Exchange
     * @return The value
     */
    public static Persistent getValueFromExchange(Exchange exchange) {
        return exchange.getIn().getBody(Persistent.class);
    }

}
