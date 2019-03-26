package org.apache.camel.component.pulsar.utils.message;

import static org.junit.Assert.assertNotNull;

import java.io.Serializable;
import org.junit.Test;

public class PulsarMessageUtilsTest {

    @Test
    public void testSerializationOfString() throws Exception {
        String in = "Hello World!";

        byte[] expected = PulsarMessageUtils.serialize(in);

        assertNotNull(expected);
    }

    @Test
    public void testSerialisationOfSerializableObject() throws Exception {
        Object in = new Obj("id", "name");

        byte[] expected = PulsarMessageUtils.serialize(in);

        assertNotNull(expected);
    }

    @Test
    public void testSerialisationOnPrimitive() throws Exception {
        byte[] expected = PulsarMessageUtils.serialize(10);

        assertNotNull(expected);
    }
}

class Obj implements Serializable {
    private String id;
    private String name;

    public Obj(String id, String name) {
        this.id = id;
        this.name = name;
    }
}