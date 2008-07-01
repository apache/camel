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
    private final String literal;
    private final String name;
    private final Class<T> type;

    private static final List<ExchangeProperty<?>> values = 
        new ArrayList<ExchangeProperty<?>>();

    private static final Map<String, ExchangeProperty<?>> literalMap = 
        new HashMap<String, ExchangeProperty<?>>();
    
    private static final Map<String, ExchangeProperty<?>> nameMap = 
        new HashMap<String, ExchangeProperty<?>>();

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
        return literalMap.get(literal);
    }

    public static ExchangeProperty<?> getByName(String name) {
        return nameMap.get(name);
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
        ExchangeProperty<?> existingProperty = literalMap.get(property.literal());
        if (existingProperty != null && existingProperty != property) {
            throw new RuntimeCamelException("An Exchange Property '" + property.literal() 
                    + "' has already been registered; its traits are: " + existingProperty.toString());
        }
        values.add(property);
        literalMap.put(property.literal(), property);
        nameMap.put(property.name(), property);
    }

    public static synchronized void deregister(ExchangeProperty<?> property) {
        if (property != null) {
            values.remove(property);
            literalMap.remove(property.literal());
            nameMap.put(property.name(), property);
        }
    }

    public static synchronized void deregister(String literal) {
        ExchangeProperty<?> property = literalMap.get(literal);
        if (property != null) {
            values.remove(property);
            literalMap.remove(property.literal());
            nameMap.put(property.name(), property);
        }
    }

    public static synchronized ExchangeProperty<?>[] values() {
        return values.toArray(new ExchangeProperty[0]);
    }

}