package org.apache.camel.component.mina;

import junit.framework.TestCase;
import org.apache.mina.common.ByteBuffer;

import java.io.InputStream;

/**
 * @version $Revision$ 
 */
public class MinaConverterTest extends TestCase {

    public void testToByteArray() {
        byte[] in = "Hello World".getBytes();
        ByteBuffer bb = ByteBuffer.wrap(in);

        byte[] out = MinaConverter.toByteArray(bb);

        for (int i = 0; i < out.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }

    public void testToString() {
        String in = "Hello World";
        ByteBuffer bb = ByteBuffer.wrap(in.getBytes());

        String out = MinaConverter.toString(bb);
        assertEquals("Hello World", out);
    }

    public void testToInputStream() throws Exception {
        byte[] in = "Hello World".getBytes();
        ByteBuffer bb = ByteBuffer.wrap(in);

        InputStream is = MinaConverter.toInputStream(bb);
        for (byte b : in) {
            int out = is.read();
            assertEquals(b, out);
        }
    }

    public void testToByteBuffer() {
        byte[] in = "Hello World".getBytes();

        ByteBuffer bb = MinaConverter.toByteBuffer(in);
        assertNotNull(bb);

        // convert back to byte[] and see if the bytes are equal
        bb.flip(); // must flip to change direction to read
        byte[] out = MinaConverter.toByteArray(bb);

        for (int i = 0; i < out.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }

}
