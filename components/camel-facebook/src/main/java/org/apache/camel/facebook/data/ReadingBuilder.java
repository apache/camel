package org.apache.camel.facebook.data;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.camel.facebook.FacebookConstants;

import facebook4j.Reading;

/**
 * Builds {@link facebook4j.Reading} instances.
 */
public class ReadingBuilder {


    public static Reading copy(Reading reading, boolean skipSinceUtil) throws NoSuchFieldException, IllegalAccessException {
        // use private field access to make a copy
        Field field = Reading.class.getDeclaredField("parameterMap");
        field.setAccessible(true);
        final LinkedHashMap<String, String> source = (LinkedHashMap<String, String>) field.get(reading);
        // create another reading, and add all fields from source
        Reading copy = new Reading();
        final LinkedHashMap copyMap = new LinkedHashMap();
        copyMap.putAll(source);
        if (skipSinceUtil) {
            copyMap.remove("since");
            copyMap.remove("until");
        }
        field.set(copy, copyMap);
        field.setAccessible(false);
        return copy;
    }

    /**
     * Sets Reading properties.
     * @param reading Reading object to populate
     * @param readingProperties Map to extract properties
     */
    public static void setProperties(Reading reading, Map<String, Object> readingProperties) {

        final String fields = (String) readingProperties.remove("fields");
        if (fields != null) {
            reading.fields(fields.toString().split(","));
        }
        final Object limit = readingProperties.remove("limit");
        if (limit != null) {
            reading.limit(Integer.parseInt(limit.toString()));
        }
        final Object offset = readingProperties.remove("offset");
        if (offset != null) {
            reading.offset(Integer.parseInt(offset.toString()));
        }
        final SimpleDateFormat dateFormat = new SimpleDateFormat(FacebookConstants.FACEBOOK_DATE_FORMAT);
        final Object until = readingProperties.remove("until");
        if (until != null) {
            try {
                reading.until(dateFormat.parse(until.toString()));
            } catch (ParseException e) {
                throw new RuntimeException("Error parsing property 'until' :" + e.getMessage(), e);
            }
        }
        final Object since = readingProperties.remove("since");
        if (since != null) {
            try {
                reading.since(dateFormat.parse(since.toString()));
            } catch (ParseException e) {
                throw new RuntimeException("Error parsing property 'since' :" + e.getMessage(), e);
            }
        }
        final Object metadata = readingProperties.remove("metadata");
        if (metadata != null && Boolean.parseBoolean(metadata.toString())) {
            reading.metadata();
        }
        final Object locale = readingProperties.remove("locale");
        if (locale != null) {
            String[] args = locale.toString().split(",");
            switch (args.length) {
            case  1:
                reading.locale(new Locale(args[0]));
                break;
            case  2:
                reading.locale(new Locale(args[0], args[1]));
                break;
            case  3:
                reading.locale(new Locale(args[0], args[1], args[2]));
                break;
            default:
                throw new IllegalArgumentException(String.format("Invalid value for property 'locale' %s, "
                    + "must be of the form [language][,country][,variant]", locale.toString()));
            }
        }
        final Object with = readingProperties.remove("with");
        if (with != null && Boolean.parseBoolean(with.toString())) {
            reading.withLocation();
        }
        final Object filter = readingProperties.remove("filter");
        if (filter != null) {
            reading.filter(filter.toString());
        }
    }

}
