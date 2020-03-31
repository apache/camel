/*
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
package org.apache.camel.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IO helper class.
 */
public final class IOHelper {

    public static Supplier<Charset> defaultCharset = Charset::defaultCharset;

    public static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private static final Logger LOG = LoggerFactory.getLogger(IOHelper.class);

    // allows to turn on backwards compatible to turn off regarding the first
    // read byte with value zero (0b0) as EOL.
    // See more at CAMEL-11672
    private static final boolean ZERO_BYTE_EOL_ENABLED = "true".equalsIgnoreCase(System.getProperty("camel.zeroByteEOLEnabled", "true"));

    private IOHelper() {
        // Utility Class
    }

    /**
     * Wraps the passed <code>in</code> into a {@link BufferedInputStream}
     * object and returns that. If the passed <code>in</code> is already an
     * instance of {@link BufferedInputStream} returns the same passed
     * <code>in</code> reference as is (avoiding double wrapping).
     *
     * @param in the wrapee to be used for the buffering support
     * @return the passed <code>in</code> decorated through a
     *         {@link BufferedInputStream} object as wrapper
     */
    public static BufferedInputStream buffered(InputStream in) {
        ObjectHelper.notNull(in, "in");
        return (in instanceof BufferedInputStream) ? (BufferedInputStream)in : new BufferedInputStream(in);
    }

    /**
     * Wraps the passed <code>out</code> into a {@link BufferedOutputStream}
     * object and returns that. If the passed <code>out</code> is already an
     * instance of {@link BufferedOutputStream} returns the same passed
     * <code>out</code> reference as is (avoiding double wrapping).
     *
     * @param out the wrapee to be used for the buffering support
     * @return the passed <code>out</code> decorated through a
     *         {@link BufferedOutputStream} object as wrapper
     */
    public static BufferedOutputStream buffered(OutputStream out) {
        ObjectHelper.notNull(out, "out");
        return (out instanceof BufferedOutputStream) ? (BufferedOutputStream)out : new BufferedOutputStream(out);
    }

    /**
     * Wraps the passed <code>reader</code> into a {@link BufferedReader} object
     * and returns that. If the passed <code>reader</code> is already an
     * instance of {@link BufferedReader} returns the same passed
     * <code>reader</code> reference as is (avoiding double wrapping).
     *
     * @param reader the wrapee to be used for the buffering support
     * @return the passed <code>reader</code> decorated through a
     *         {@link BufferedReader} object as wrapper
     */
    public static BufferedReader buffered(Reader reader) {
        ObjectHelper.notNull(reader, "reader");
        return (reader instanceof BufferedReader) ? (BufferedReader)reader : new BufferedReader(reader);
    }

    /**
     * Wraps the passed <code>writer</code> into a {@link BufferedWriter} object
     * and returns that. If the passed <code>writer</code> is already an
     * instance of {@link BufferedWriter} returns the same passed
     * <code>writer</code> reference as is (avoiding double wrapping).
     *
     * @param writer the wrapee to be used for the buffering support
     * @return the passed <code>writer</code> decorated through a
     *         {@link BufferedWriter} object as wrapper
     */
    public static BufferedWriter buffered(Writer writer) {
        ObjectHelper.notNull(writer, "writer");
        return (writer instanceof BufferedWriter) ? (BufferedWriter)writer : new BufferedWriter(writer);
    }

    public static String toString(Reader reader) throws IOException {
        return toString(buffered(reader));
    }

    public static String toString(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[1024];
        try {
            int len;
            // read until we reach then end which is the -1 marker
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
        } finally {
            IOHelper.close(reader, "reader", LOG);
        }

        return sb.toString();
    }

    public static int copy(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    public static int copy(final InputStream input, final OutputStream output, int bufferSize) throws IOException {
        return copy(input, output, bufferSize, false);
    }

    public static int copy(final InputStream input, final OutputStream output, int bufferSize, boolean flushOnEachWrite) throws IOException {
        if (input instanceof ByteArrayInputStream) {
            // optimized for byte array as we only need the max size it can be
            input.mark(0);
            input.reset();
            bufferSize = input.available();
        } else {
            int avail = input.available();
            if (avail > bufferSize) {
                bufferSize = avail;
            }
        }

        if (bufferSize > 262144) {
            // upper cap to avoid buffers too big
            bufferSize = 262144;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Copying InputStream: {} -> OutputStream: {} with buffer: {} and flush on each write {}", input, output, bufferSize, flushOnEachWrite);
        }

        int total = 0;
        final byte[] buffer = new byte[bufferSize];
        int n = input.read(buffer);

        boolean hasData;
        if (ZERO_BYTE_EOL_ENABLED) {
            // workaround issue on some application servers which can return 0
            // (instead of -1)
            // as first byte to indicate end of stream (CAMEL-11672)
            hasData = n > 0;
        } else {
            hasData = n > -1;
        }
        if (hasData) {
            while (-1 != n) {
                output.write(buffer, 0, n);
                if (flushOnEachWrite) {
                    output.flush();
                }
                total += n;
                n = input.read(buffer);
            }
        }
        if (!flushOnEachWrite) {
            // flush at end, if we didn't do it during the writing
            output.flush();
        }
        return total;
    }

    public static void copyAndCloseInput(InputStream input, OutputStream output) throws IOException {
        copyAndCloseInput(input, output, DEFAULT_BUFFER_SIZE);
    }

    public static void copyAndCloseInput(InputStream input, OutputStream output, int bufferSize) throws IOException {
        copy(input, output, bufferSize);
        close(input, null, LOG);
    }

    public static int copy(final Reader input, final Writer output, int bufferSize) throws IOException {
        final char[] buffer = new char[bufferSize];
        int n = input.read(buffer);
        int total = 0;
        while (-1 != n) {
            output.write(buffer, 0, n);
            total += n;
            n = input.read(buffer);
        }
        output.flush();
        return total;
    }

    public static void transfer(ReadableByteChannel input, WritableByteChannel output) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        while (input.read(buffer) >= 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                output.write(buffer);
            }
            buffer.clear();
        }
    }

    /**
     * Forces any updates to this channel's file to be written to the storage
     * device that contains it.
     *
     * @param channel the file channel
     * @param name the name of the resource
     * @param log the log to use when reporting warnings, will use this class's
     *            own {@link Logger} if <tt>log == null</tt>
     */
    public static void force(FileChannel channel, String name, Logger log) {
        try {
            if (channel != null) {
                channel.force(true);
            }
        } catch (Exception e) {
            if (log == null) {
                // then fallback to use the own Logger
                log = LOG;
            }
            if (name != null) {
                log.warn("Cannot force FileChannel: " + name + ". Reason: " + e.getMessage(), e);
            } else {
                log.warn("Cannot force FileChannel. Reason: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Forces any updates to a FileOutputStream be written to the storage device
     * that contains it.
     *
     * @param os the file output stream
     * @param name the name of the resource
     * @param log the log to use when reporting warnings, will use this class's
     *            own {@link Logger} if <tt>log == null</tt>
     */
    public static void force(FileOutputStream os, String name, Logger log) {
        try {
            if (os != null) {
                os.getFD().sync();
            }
        } catch (Exception e) {
            if (log == null) {
                // then fallback to use the own Logger
                log = LOG;
            }
            if (name != null) {
                log.warn("Cannot sync FileDescriptor: " + name + ". Reason: " + e.getMessage(), e);
            } else {
                log.warn("Cannot sync FileDescriptor. Reason: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Closes the given writer, logging any closing exceptions to the given log.
     * An associated FileOutputStream can optionally be forced to disk.
     *
     * @param writer the writer to close
     * @param os an underlying FileOutputStream that will to be forced to disk
     *            according to the force parameter
     * @param name the name of the resource
     * @param log the log to use when reporting warnings, will use this class's
     *            own {@link Logger} if <tt>log == null</tt>
     * @param force forces the FileOutputStream to disk
     */
    public static void close(Writer writer, FileOutputStream os, String name, Logger log, boolean force) {
        if (writer != null && force) {
            // flush the writer prior to syncing the FD
            try {
                writer.flush();
            } catch (Exception e) {
                if (log == null) {
                    // then fallback to use the own Logger
                    log = LOG;
                }
                if (name != null) {
                    log.warn("Cannot flush Writer: " + name + ". Reason: " + e.getMessage(), e);
                } else {
                    log.warn("Cannot flush Writer. Reason: {}", e.getMessage(), e);
                }
            }
            force(os, name, log);
        }
        close(writer, name, log);
    }

    /**
     * Closes the given resource if it is available, logging any closing
     * exceptions to the given log.
     *
     * @param closeable the object to close
     * @param name the name of the resource
     * @param log the log to use when reporting closure warnings, will use this
     *            class's own {@link Logger} if <tt>log == null</tt>
     */
    public static void close(Closeable closeable, String name, Logger log) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (log == null) {
                    // then fallback to use the own Logger
                    log = LOG;
                }
                if (name != null) {
                    log.warn("Cannot close: " + name + ". Reason: " + e.getMessage(), e);
                } else {
                    log.warn("Cannot close. Reason: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Closes the given resource if it is available and don't catch the
     * exception
     *
     * @param closeable the object to close
     * @throws IOException
     */
    public static void closeWithException(Closeable closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Closes the given channel if it is available, logging any closing
     * exceptions to the given log. The file's channel can optionally be forced
     * to disk.
     *
     * @param channel the file channel
     * @param name the name of the resource
     * @param log the log to use when reporting warnings, will use this class's
     *            own {@link Logger} if <tt>log == null</tt>
     * @param force forces the file channel to disk
     */
    public static void close(FileChannel channel, String name, Logger log, boolean force) {
        if (force) {
            force(channel, name, log);
        }
        close(channel, name, log);
    }

    /**
     * Closes the given resource if it is available.
     *
     * @param closeable the object to close
     * @param name the name of the resource
     */
    public static void close(Closeable closeable, String name) {
        close(closeable, name, LOG);
    }

    /**
     * Closes the given resource if it is available.
     *
     * @param closeable the object to close
     */
    public static void close(Closeable closeable) {
        close(closeable, null, LOG);
    }

    /**
     * Closes the given resources if they are available.
     *
     * @param closeables the objects to close
     */
    public static void close(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            close(closeable);
        }
    }

    public static void closeIterator(Object it) throws IOException {
        if (it instanceof Closeable) {
            IOHelper.closeWithException((Closeable)it);
        }
        if (it instanceof java.util.Scanner) {
            IOException ioException = ((java.util.Scanner)it).ioException();
            if (ioException != null) {
                throw ioException;
            }
        }
    }

    public static void validateCharset(String charset) throws UnsupportedCharsetException {
        if (charset != null) {
            if (Charset.isSupported(charset)) {
                Charset.forName(charset);
                return;
            }
        }
        throw new UnsupportedCharsetException(charset);
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = buffered(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            close(isr, in);
        }
    }

    /**
     * Get the charset name from the content type string
     *
     * @param contentType
     * @return the charset name, or <tt>UTF-8</tt> if no found
     */
    public static String getCharsetNameFromContentType(String contentType) {
        String[] values = contentType.split(";");
        String charset = "";

        for (String value : values) {
            value = value.trim();
            if (value.toLowerCase().startsWith("charset=")) {
                // Take the charset name
                charset = value.substring(8);
            }
        }
        if ("".equals(charset)) {
            charset = "UTF-8";
        }
        return normalizeCharset(charset);

    }

    /**
     * This method will take off the quotes and double quotes of the charset
     */
    public static String normalizeCharset(String charset) {
        if (charset != null) {
            String answer = charset.trim();
            if (answer.startsWith("'") || answer.startsWith("\"")) {
                answer = answer.substring(1);
            }
            if (answer.endsWith("'") || answer.endsWith("\"")) {
                answer = answer.substring(0, answer.length() - 1);
            }
            return answer.trim();
        } else {
            return null;
        }
    }

    /**
     * Lookup the OS environment variable in a safe manner by
     * using upper case keys and underscore instead of dash.
     */
    public static String lookupEnvironmentVariable(String key) {
        // lookup OS env with upper case key
        String upperKey = key.toUpperCase();
        String value = System.getenv(upperKey);

        if (value == null) {
            // some OS do not support dashes in keys, so replace with underscore
            String normalizedKey = upperKey.replace('-', '_');

            // and replace dots with underscores so keys like my.key are
            // translated to MY_KEY
            normalizedKey = normalizedKey.replace('.', '_');

            value = System.getenv(normalizedKey);
        }
        return value;
    }

    /**
     * Encoding-aware input stream.
     */
    public static class EncodingInputStream extends InputStream {

        private final File file;
        private final BufferedReader reader;
        private final Charset defaultStreamCharset;

        private ByteBuffer bufferBytes;
        private CharBuffer bufferedChars = CharBuffer.allocate(4096);

        public EncodingInputStream(File file, String charset) throws IOException {
            this.file = file;
            reader = toReader(file, charset);
            defaultStreamCharset = defaultCharset.get();
        }

        @Override
        public int read() throws IOException {
            if (bufferBytes == null || bufferBytes.remaining() <= 0) {
                BufferCaster.cast(bufferedChars).clear();
                int len = reader.read(bufferedChars);
                bufferedChars.flip();
                if (len == -1) {
                    return -1;
                }
                bufferBytes = defaultStreamCharset.encode(bufferedChars);
            }
            return bufferBytes.get();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        @Override
        public synchronized void reset() throws IOException {
            reader.reset();
        }

        public InputStream toOriginalInputStream() throws FileNotFoundException {
            return new FileInputStream(file);
        }
    }

    /**
     * Encoding-aware file reader.
     */
    public static class EncodingFileReader extends InputStreamReader {

        private final FileInputStream in;

        /**
         * @param in file to read
         * @param charset character set to use
         */
        public EncodingFileReader(FileInputStream in, String charset) throws FileNotFoundException, UnsupportedEncodingException {
            super(in, charset);
            this.in = in;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                in.close();
            }
        }
    }

    /**
     * Encoding-aware file writer.
     */
    public static class EncodingFileWriter extends OutputStreamWriter {

        private final FileOutputStream out;

        /**
         * @param out file to write
         * @param charset character set to use
         */
        public EncodingFileWriter(FileOutputStream out, String charset) throws FileNotFoundException, UnsupportedEncodingException {
            super(out, charset);
            this.out = out;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                out.close();
            }
        }
    }

    /**
     * Converts the given {@link File} with the given charset to {@link InputStream} with the JVM default charset
     *
     * @param file the file to be converted
     * @param charset the charset the file is read with
     * @return the input stream with the JVM default charset
     */
    public static InputStream toInputStream(File file, String charset) throws IOException {
        if (charset != null) {
            return new EncodingInputStream(file, charset);
        } else {
            return buffered(new FileInputStream(file));
        }
    }

    public static BufferedReader toReader(File file, String charset) throws IOException {
        FileInputStream in = new FileInputStream(file);
        return IOHelper.buffered(new EncodingFileReader(in, charset));
    }

    public static BufferedWriter toWriter(FileOutputStream os, String charset) throws IOException {
        return IOHelper.buffered(new EncodingFileWriter(os, charset));
    }
}
