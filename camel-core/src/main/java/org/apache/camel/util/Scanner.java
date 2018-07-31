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
package org.apache.camel.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Scanner implements Iterator<String>, Closeable {

    private static final Map<String, Pattern> CACHE = LRUCacheFactory.newLRUCache(7);

    private static final String WHITESPACE_PATTERN = "\\s+";

    private static final String FIND_ANY_PATTERN = "(?s).*";

    private static final int BUFFER_SIZE = 1024;

    private Readable source;
    private Pattern delimPattern;
    private Matcher matcher;
    private CharBuffer buf;
    private int position;
    private boolean inputExhausted;
    private boolean needInput;
    private boolean skipped;
    private int savedPosition = -1;
    private boolean closed;
    private IOException lastIOException;

    public Scanner(InputStream source, String charsetName, String pattern) {
        this(new InputStreamReader(Objects.requireNonNull(source, "source"), toDecoder(charsetName)), cachePattern(pattern));
    }

    public Scanner(File source, String charsetName, String pattern) throws FileNotFoundException {
        this(new FileInputStream(Objects.requireNonNull(source, "source")).getChannel(), charsetName, pattern);
    }

    public Scanner(String source, String pattern) {
        this(new StringReader(Objects.requireNonNull(source, "source")), cachePattern(pattern));
    }

    public Scanner(ReadableByteChannel source, String charsetName, String pattern) {
        this(Channels.newReader(Objects.requireNonNull(source, "source"), toDecoder(charsetName), -1), cachePattern(pattern));
    }

    public Scanner(Readable source, String pattern) {
        this(Objects.requireNonNull(source, "source"), cachePattern(pattern));
    }

    private Scanner(Readable source, Pattern pattern) {
        this.source = source;
        delimPattern = pattern != null ? pattern : cachePattern(WHITESPACE_PATTERN);
        buf = CharBuffer.allocate(BUFFER_SIZE);
        buf.limit(0);
        matcher = delimPattern.matcher(buf);
        matcher.useTransparentBounds(true);
        matcher.useAnchoringBounds(false);
    }

    private static CharsetDecoder toDecoder(String charsetName) {
        try {
            Charset cs = charsetName != null ? Charset.forName(charsetName) : Charset.defaultCharset();
            return cs.newDecoder();
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public boolean hasNext() {
        checkClosed();
        saveState();
        while (!inputExhausted) {
            if (hasTokenInBuffer()) {
                revertState();
                return true;
            }
            readMore();
        }
        boolean result = hasTokenInBuffer();
        revertState();
        return result;
    }

    public String next() {
        checkClosed();
        while (true) {
            String token = getCompleteTokenInBuffer();
            if (token != null) {
                skipped = false;
                return token;
            }
            if (needInput) {
                readMore();
            } else {
                throwFor();
            }
        }
    }

    private void saveState() {
        savedPosition = position;
    }

    private void revertState() {
        position = savedPosition;
        savedPosition = -1;
        skipped = false;
    }

    private void readMore() {
        if (buf.limit() == buf.capacity()) {
            expandBuffer();
        }
        int p = buf.position();
        buf.position(buf.limit());
        buf.limit(buf.capacity());
        int n;
        try {
            n = source.read(buf);
        } catch (IOException ioe) {
            lastIOException = ioe;
            n = -1;
        }
        if (n == -1) {
            inputExhausted = true;
            needInput = false;
        } else if (n > 0) {
            needInput = false;
        }
        buf.limit(buf.position());
        buf.position(p);
    }

    private void expandBuffer() {
        int offset = savedPosition == -1 ? position : savedPosition;
        buf.position(offset);
        if (offset > 0) {
            buf.compact();
            translateSavedIndexes(offset);
            position -= offset;
            buf.flip();
        } else {
            int newSize = buf.capacity() * 2;
            CharBuffer newBuf = CharBuffer.allocate(newSize);
            newBuf.put(buf);
            newBuf.flip();
            translateSavedIndexes(offset);
            position -= offset;
            buf = newBuf;
            matcher.reset(buf);
        }
    }

    private void translateSavedIndexes(int offset) {
        if (savedPosition != -1) {
            savedPosition -= offset;
        }
    }

    private void throwFor() {
        skipped = false;
        if (inputExhausted && position == buf.limit()) {
            throw new NoSuchElementException();
        } else {
            throw new InputMismatchException();
        }
    }

    private boolean hasTokenInBuffer() {
        matcher.usePattern(delimPattern);
        matcher.region(position, buf.limit());
        if (matcher.lookingAt()) {
            position = matcher.end();
        }
        return position != buf.limit();
    }

    private String getCompleteTokenInBuffer() {
        matcher.usePattern(delimPattern);
        if (!skipped) {
            matcher.region(position, buf.limit());
            if (matcher.lookingAt()) {
                if (matcher.hitEnd() && !inputExhausted) {
                    needInput = true;
                    return null;
                }
                skipped = true;
                position = matcher.end();
            }
        }
        if (position == buf.limit()) {
            if (inputExhausted) {
                return null;
            }
            needInput = true;
            return null;
        }
        matcher.region(position, buf.limit());
        boolean foundNextDelim = matcher.find();
        if (foundNextDelim && (matcher.end() == position)) {
            foundNextDelim = matcher.find();
        }
        if (foundNextDelim) {
            if (matcher.requireEnd() && !inputExhausted) {
                needInput = true;
                return null;
            }
            int tokenEnd = matcher.start();
            matcher.usePattern(cachePattern(FIND_ANY_PATTERN));
            matcher.region(position, tokenEnd);
            if (matcher.matches()) {
                String s = matcher.group();
                position = matcher.end();
                return s;
            } else {
                return null;
            }
        }
        if (inputExhausted) {
            matcher.usePattern(cachePattern(FIND_ANY_PATTERN));
            matcher.region(position, buf.limit());
            if (matcher.matches()) {
                String s = matcher.group();
                position = matcher.end();
                return s;
            }
            return null;
        }
        needInput = true;
        return null;
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException();
        }
    }

    public void close() {
        if (!closed) {
            if (source instanceof Closeable) {
                try {
                    ((Closeable) source).close();
                } catch (IOException e) {
                    lastIOException = e;
                }
            }
            closed = true;
        }
    }

    public IOException ioException() {
        return lastIOException;
    }

    private static Pattern cachePattern(String pattern) {
        if (pattern == null) {
            return null;
        }
        return CACHE.computeIfAbsent(pattern, new Function<String, Pattern>() {
            @Override
            public Pattern apply(String s) {
                return Pattern.compile(s);
            }
        });
    }

}
