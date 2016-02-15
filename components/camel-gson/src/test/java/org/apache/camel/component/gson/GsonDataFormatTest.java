package org.apache.camel.component.gson;

import java.io.*;
import java.util.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GsonDataFormatTest {
    @Test
    public void testString() throws Exception {
        testJson("\"A string\"", "A string");
    }

    @Test
    public void testMap() throws Exception {
        testJson("{value=123}", Collections.singletonMap("value", 123.0));
    }

    @Test
    public void testList() throws Exception {
        testJson("[{value=123}]", Collections.singletonList(Collections.singletonMap("value", 123.0)));
    }

    private void testJson(String json, Object expected) throws Exception {
        Object unmarshalled;
        GsonDataFormat gsonDataFormat = new GsonDataFormat();
        gsonDataFormat.doStart();
        try (InputStream in = new ByteArrayInputStream(json.getBytes())) {
            unmarshalled = gsonDataFormat.unmarshal(null, in);
        }

        assertEquals(expected, unmarshalled);
    }
}
