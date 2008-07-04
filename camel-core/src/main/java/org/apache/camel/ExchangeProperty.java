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
package org.apache.camel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an instance and a type safe registry of well known Camel Exchange properties.
 * <p/>
 * <b>Usage pattern:</b>
 * <br/>In your code register a property that you wish to pass via Camel Exchange:
 * <pre>
 *      public static final ExchangeProperty<Boolean> myProperty =
 *            new ExchangeProperty<Boolean>("myProperty", "org.apache.myproject.mypackage.myproperty", Boolean.class);
 *
 *  Then in your code set this property's value:
 *      myProperty.set(exchange, Boolean.TRUE);
 *
 *  Check the value of this property where required:
 *      ExchangeProperty<?> property = ExchangeProperty.get("myProperty");
 *      if (property != null && property.get(exchange) == Boolean.TRUE) {
 *           // do your thing ...
 *       }
 *  Or
 *      Boolean value = myProperty.get(exchange);
 *      if (value == Boolean.TRUE) {
 *          // do your thing
 *      }
 *
 *  When your code no longer requires this property then deregister it:
 *      ExchangeProperty.deregister(myProperty);
 *  Or
 *      ExchangeProperty.deregister("myProperty");
 *  </pre>
 *
 *  <b>Note:</b> that if ExchangeProperty instance get or set methods are used then type checks
 *  of property's value are performed and a runtime exception can be thrown if type
 *  safety is violated.
 */
public class ExchangeProperty<T> {

    private static final List<ExchangeProperty<?>> VALUES =
        new ArrayList<ExchangeProperty<?>>();

    private static final Map<String, ExchangeProperty<?>> LITERAL_MAP =
        new HashMap<String, ExchangeProperty<?>>();

    private static final Map<String, ExchangeProperty<?>> NAME_MAP =
        new HashMap<String, ExchangeProperty<?>>();

    private final String literal;
    private final String name;
    private final Class<T> type;

    public ExchangeProperty(String literal, String name, Class<T> type) {
        this.literal = literal;
        this.name = name;
        this.type = type;
        register(this);
    }

    public String literal() {
        return literal;
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    public T get(Exchange exchange) {
        return exchange.getProperty(name, type);
    }

    public static ExchangeProperty<?> get(String literal) {
        return LITERAL_MAP.get(literal);
    }

    public static ExchangeProperty<?> getByName(String name) {
        return NAME_MAP.get(name);
    }

    public T set(Exchange exchange, T value) {
        T oldValue = get(exchange);
        exchange.setProperty(name, value);
        return oldValue;
    }

    public T remove(Exchange exchange) {
        T oldValue = get(exchange);
        exchange.removeProperty(name);
        return oldValue;
    }

    @Override
    public String toString() {
        return type().getCanonicalName() + " " + name + " (" + literal() + ")";
    }

    public static synchronized void register(ExchangeProperty<?> property) {
        ExchangeProperty<?> existingProperty = LITERAL_MAP.get(property.literal());
        if (existingProperty != null && existingProperty != property) {
            throw new RuntimeCamelException("An Exchange Property '" + property.literal()
                    + "' has already been registered; its traits are: " + existingProperty.toString());
        }
        VALUES.add(property);
        LITERAL_MAP.put(property.literal(), property);
        NAME_MAP.put(property.name(), property);
    }

    public static synchronized void deregister(ExchangeProperty<?> property) {
        if (property != null) {
            VALUES.remove(property);
            LITERAL_MAP.remove(property.literal());
            NAME_MAP.put(property.name(), property);
        }
    }

    public static synchronized void deregister(String literal) {
        ExchangeProperty<?> property = LITERAL_MAP.get(literal);
        if (property != null) {
            VALUES.remove(property);
            LITERAL_MAP.remove(property.literal());
            NAME_MAP.put(property.name(), property);
        }
    }

    public static synchronized ExchangeProperty<?>[] values() {
        return VALUES.toArray(new ExchangeProperty[0]);
    }

}