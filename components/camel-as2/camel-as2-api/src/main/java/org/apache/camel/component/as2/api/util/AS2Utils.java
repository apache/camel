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

package org.apache.camel.component.as2.api.util;

import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.as2.api.InvalidAS2NameException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * Utility Methods used in AS2 Component
 */
public final class AS2Utils {

    public static final String DQUOTE = "\"";
    public static final String BACKSLASH = "\\\\";
    public static final String AS2_TEXT_CHAR_SET = "[\u0021\u0023-\\\u005B\\\u005D-\u007E]";
    public static final String AS2_QUOTED_TEXT_CHAR_SET = "[\u0020\u0021\u0023-\\\u005B\\\u005D-\u007E]";
    public static final String AS2_QUOTED_PAIR = BACKSLASH + DQUOTE + "|" + BACKSLASH + BACKSLASH;

    public static final String AS2_QUOTED_NAME =
            DQUOTE + "(" + AS2_QUOTED_TEXT_CHAR_SET + "|" + AS2_QUOTED_PAIR + "){1,128}" + DQUOTE;
    public static final String AS2_ATOMIC_NAME = "(" + AS2_TEXT_CHAR_SET + "){1,128}";
    public static final String AS2_NAME = AS2_ATOMIC_NAME + "|" + AS2_QUOTED_NAME;

    public static final Pattern AS_NAME_PATTERN = Pattern.compile(AS2_NAME);

    private static SecureRandom generator = new SecureRandom();

    private AS2Utils() {}

    /**
     * Validates if the given <code>name</code> is a valid AS2 Name
     *
     * @param  name                    - the name to validate.
     * @throws InvalidAS2NameException - If <code>name</code> is invalid.
     */
    public static void validateAS2Name(String name) throws InvalidAS2NameException {
        Matcher matcher = AS_NAME_PATTERN.matcher(name);
        if (!matcher.matches()) {
            // if name does not match, determine where it fails to match.
            int i;
            for (i = name.length() - 1; i > 0; i--) {
                Matcher region = matcher.region(0, i);
                if (region.matches() || region.hitEnd()) {
                    break;
                }
            }
            throw new InvalidAS2NameException(name, i);
        }
    }

    /**
     * Generates a globally unique message ID which includes <code>fqdn</code>: a fully qualified domain name (FQDN)
     *
     * @param  fqdn - the fully qualified domain name to use in message id.
     * @return      The generated message id.
     */
    public static String createMessageId(String fqdn) {
        /* Wall Clock Time in Nanoseconds */
        /* 64 Bit Random Number */
        /* Fully Qualified Domain Name */
        return "<" + Long.toString(System.nanoTime(), 36) + "." + Long.toString(generator.nextLong(), 36) + "@" + fqdn
                + ">";
    }

    /**
     * Determines if <code>c</code> is a printable character.
     *
     * @param  c - the character to test
     * @return   <code>true</code> if <code>c</code> is a printable character; <code>false</code> otherwise.
     */
    public static boolean isPrintableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return !Character.isISOControl(c)
                && c != KeyEvent.CHAR_UNDEFINED
                && block != null
                && block != Character.UnicodeBlock.SPECIALS;
    }

    public static String printRequest(HttpRequest request) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos, true, "utf-8")) {
            printRequest(ps, request);
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static String printMessage(HttpMessage message) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos, true, "utf-8")) {
            printMessage(ps, message);
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * Prints the contents of request to given print stream.
     *
     * @param  out         - the stream printed to.
     * @param  request     - the request printed.
     * @throws IOException - If failed to print request.
     */
    public static void printRequest(PrintStream out, HttpRequest request) throws IOException {
        // Print request line
        out.println(new RequestLine(request));
        // Write headers
        for (final Iterator<Header> it = request.headerIterator(); it.hasNext(); ) {
            Header header = it.next();
            out.println(header.getName() + ": " + (header.getValue() == null ? "" : header.getValue()));
        }
        out.println(); // write empty line separating header from body.

        if (request instanceof BasicClassicHttpRequest) {
            // Write entity
            HttpEntity entity = ((BasicClassicHttpRequest) request).getEntity();
            entity.writeTo(out);
        }
    }

    /**
     * Prints the contents of an Http Message to given print stream.
     *
     * @param  out         - the stream printed to.
     * @param  message     - the request printed.
     * @throws IOException - If failed to print message.
     */
    public static void printMessage(PrintStream out, HttpMessage message) throws IOException {
        // Print request line
        if (message instanceof HttpRequest) {
            out.println(new RequestLine((HttpRequest) message));
        } else { // HttpResponse
            out.println(new StatusLine((HttpResponse) message));
        }
        // Write headers
        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            Header header = it.next();
            out.println(header.getName() + ": " + (header.getValue() == null ? "" : header.getValue()));
        }
        out.println(); // write empty line separating header from body.

        if (message instanceof BasicClassicHttpRequest) {
            // Write entity
            HttpEntity entity = ((BasicClassicHttpRequest) message).getEntity();
            if (entity != null) {
                entity.writeTo(out);
            }
        } else if (message instanceof BasicClassicHttpResponse) {
            // Write entity
            HttpEntity entity = ((BasicClassicHttpResponse) message).getEntity();
            if (entity != null) {
                entity.writeTo(out);
            }
        }
    }
}
