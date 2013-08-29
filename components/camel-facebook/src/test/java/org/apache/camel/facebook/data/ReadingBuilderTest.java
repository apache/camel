package org.apache.camel.facebook.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

import facebook4j.Reading;
import org.apache.camel.facebook.FacebookConstants;
import org.junit.Test;

/**
 * Test {@link ReadingBuilder}. 
 */
public class ReadingBuilderTest {

    @Test
    public void testCopy() throws Exception {
        final Reading source = new Reading();
        source.fields("field1", "field2");
        source.filter("testFilter");
        source.limit(100);
        source.locale(Locale.US);
        source.metadata();
        source.offset(1000);
        source.since(new Date());
        source.until(new Date());
        source.withLocation();
        
        Reading copy = ReadingBuilder.copy(source, false);
        assertNotNull("Null copy", copy);
        assertEquals("Copy not equal", source.toString(), copy.toString());

        // skip since and until
        copy = ReadingBuilder.copy(source, true);
        assertNotEquals("Copy equal", source.toString(), copy.toString());
        assertFalse("since", copy.toString().contains("since="));
        assertFalse("until", copy.toString().contains("until="));
    }

    @Test
    public void testSetProperties() throws Exception {
        final Reading reading = new Reading();

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("fields", "field1,field2");
        properties.put("filter", "testFilter");
        properties.put("limit", "100");
        properties.put("metadata", "");
        properties.put("offset", "1000");
        final String facebookDate = new SimpleDateFormat(FacebookConstants.FACEBOOK_DATE_FORMAT).format(new Date());
        properties.put("since", facebookDate);
        properties.put("until", facebookDate);
        properties.put("withLocation", "");

        // set properties on Reading
        ReadingBuilder.setProperties(reading, properties);
    }

}
