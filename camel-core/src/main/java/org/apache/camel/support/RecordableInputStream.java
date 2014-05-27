/**
 * 
 */
package org.apache.camel.support;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * This class is used by the toknizer to extract data while reading from the stream.
 * REVIST it is used package internally but may be moved to some common package.
 */
class RecordableInputStream extends FilterInputStream {
    private TrimmableByteArrayOutputStream buf;
    private String charset;
    private boolean recording;
    protected RecordableInputStream(InputStream in, String charset) {
        super(in);
        this.buf = new TrimmableByteArrayOutputStream();
        this.charset = charset;
        this.recording = true;
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c > 0 && recording) {
            buf.write(c);
        }
        return c;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0 && recording) {
            buf.write(b, off, n);
        }
        return n;
    }

    public String getText(int pos) {
        String t = null;
        recording = false;
        final byte[] ba = buf.toByteArray(pos);
        buf.trim(pos, 0);
        try {        
            if (charset == null) {
                t = new String(ba);
            } else {
                t = new String(ba, charset);
            }
        } catch (UnsupportedEncodingException e) {
            // ignore it as this encoding exception should have been caught earlier while scanning.
        }

        return t;
    }
    
    public byte[] getBytes(int pos) {
        recording = false;
        byte[] b = buf.toByteArray(pos);
        buf.trim(pos, 0);
        return b;
    }
    
    public void record() {
        recording = true;
    }

    private static class TrimmableByteArrayOutputStream extends ByteArrayOutputStream {
        public void trim(int head, int tail) {
            System.arraycopy(buf, head, buf, 0, count - head - tail);
            count -= head + tail;
        }
        
        public byte[] toByteArray(int len) {
            byte[] b = new byte[len];
            System.arraycopy(buf, 0, b, 0, len);
            return b;
        }
    }

}
