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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimerTask;
import org.apache.camel.util.concurrent.Rejectable;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilEdgeCasesTest {

    @TempDir
    Path tempDir;

    @Test
    public void testQueryStringWithEmptyList() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "1");
        map.put("b", List.of());
        map.put("c", "2");
        assertEquals("a=1&c=2", URISupport.createQueryString(map));

        map.remove("c");
        assertEquals("a=1", URISupport.createQueryString(map));
    }

    @Test
    public void testParseQueryWithDoubleAmpersand() throws Exception {
        Map<String, Object> map = URISupport.parseQuery("a=1&&b=2");
        assertEquals(Map.of("a", "1", "b", "2"), map);
    }

    @Test
    public void testStripExtOfHiddenFileInRoot() {
        assertEquals("/.bashrc", FileUtil.stripExt("/.bashrc"));
        assertEquals("/.a", FileUtil.stripExt("/.a.b"));
        assertEquals("/a", FileUtil.stripExt("/a.b"));
    }

    @Test
    public void testCompactPathKeepsDrive() {
        assertEquals("C:/b", FileUtil.compactPath("C:/a/../../b", '/'));
        assertEquals("C:/b", FileUtil.compactPath("C:/a/../b", '/'));
    }

    @Test
    public void testCharsetNameFromContentType() {
        assertEquals("iso-8859-1", IOHelper.getCharsetNameFromContentType("text/plain; mycharset=foo; charset=iso-8859-1"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("text/plain; charset=utf-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("text/plain;charset=UTF-8"));
        assertEquals("utf-16", IOHelper.getCharsetNameFromContentType("text/plain; charset=utf-16"));
    }

    @Test
    public void testEncodingInputStreamWithSurrogatePairAtBufferEnd() throws Exception {
        String text = "a".repeat(4095) + "\uD83D\uDE00" + "end";
        Path file = tempDir.resolve("emoji.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);

        try (InputStream is = new IOHelper.EncodingInputStream(file, "UTF-8")) {
            assertEquals(text, new String(is.readAllBytes(), IOHelper.defaultCharset.get()));
        }
    }

    @Test
    public void testTimePattern() {
        assertEquals(5000, TimeUtils.toMilliSeconds("5S"));
        assertEquals(-5000, TimeUtils.toMilliSeconds("-5s"));
        assertEquals(-500, TimeUtils.toMilliSeconds("-500ms"));
        assertEquals(90000, TimeUtils.toMilliSeconds("1m30s"));
        assertEquals(5400000, TimeUtils.toMilliSeconds("1h 30m"));
        assertEquals(5000, TimeUtils.toMilliSeconds("5sec"));
        assertEquals(93784005, TimeUtils.toMilliSeconds("1d2h3m4s5ms"));
    }

    @Test
    public void testInvalidTimePattern() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.toMilliSeconds("five"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.toMilliSeconds("1m30"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.toMilliSeconds("abc"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.toMilliSeconds("s"));
    }

    @Test
    public void testScannerDelimiterAcrossBufferBoundary() throws Exception {
        String in = "xxxxxx;".repeat(146) + "\r\n\r\n";
        int n = 0;
        try (Scanner s = new Scanner(in, "\\s*;\\s*")) {
            while (s.hasNext()) {
                s.next();
                n++;
            }
        }
        assertEquals(146, n);
    }

    @Test
    public void testNormalizeWhitespace() {
        assertEquals("a b", StringHelper.normalizeWhitespace("a \t b"));
        assertEquals("a b", StringHelper.normalizeWhitespace("a\t\tb"));
        assertEquals("a b c", StringHelper.normalizeWhitespace("a  b\t\tc"));
        assertEquals("a b", StringHelper.normalizeWhitespace("a\nb"));
    }

    @Test
    public void testReplaceFromSecondOccurrence() {
        assertEquals("a:x/C:\\dir", StringHelper.replaceFromSecondOccurrence("a:x/x", "x", "C:\\dir"));
        assertEquals("a:x/p$1", StringHelper.replaceFromSecondOccurrence("a:x/x", "x", "p$1"));
        assertEquals("x/v/v/v", StringHelper.replaceFromSecondOccurrence("x/x/x/x", "x", "v"));
    }

    @Test
    public void testDashToCamelCaseKeepsKeysAfterQuotes() {
        assertEquals("camel.foo['a'].bar[x-y].myOpt",
                StringHelper.dashToCamelCase("camel.foo['a'].bar[x-y].my-opt", true));
        assertEquals("camel.args[it's-x].myOpt", StringHelper.dashToCamelCase("camel.args[it's-x].my-opt", true));
    }

    @Test
    public void testRemoveStartingCharacters() {
        assertEquals("", StringHelper.removeStartingCharacters("///", '/'));
        assertEquals("", StringHelper.removeStartingCharacters("", '/'));
    }

    @Test
    public void testBackOffMaxDelay() {
        BackOff backOff = BackOff.builder().delay(2000).maxDelay(60000).multiplier(2d).build();
        BackOffTimerTask task = new BackOffTimerTask(null, backOff, null, t -> true);
        List<Long> delays = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            delays.add(task.next());
        }
        assertEquals(List.of(4000L, 8000L, 16000L, 32000L, 60000L, 60000L, 60000L), delays);
    }

    @Test
    public void testBackOffReset() {
        BackOff backOff = BackOff.builder().delay(Duration.ofSeconds(1)).build();
        BackOffTimerTask task = new BackOffTimerTask(null, backOff, null, t -> true);
        task.next();
        task.next();
        task.reset();
        assertEquals(1000, task.next());
        assertEquals(1000, task.next());
    }

    @Test
    public void testExceptionWithCyclicCause() {
        Exception a = new Exception("a");
        IOException b = new IOException("b", a);
        a.initCause(b);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertSame(b, ObjectHelper.getException(IOException.class, a));
            assertNull(ObjectHelper.getException(IllegalStateException.class, a));
        });
    }

    @Test
    public void testLoadArrayClasses() {
        assertEquals(int[].class, ObjectHelper.loadClass("int[]"));
        assertEquals(long[].class, ObjectHelper.loadClass("long[]"));
        assertEquals(Integer[].class, ObjectHelper.loadClass("Integer[]"));
        assertEquals(String[][].class, ObjectHelper.loadClass("java.lang.String[][]"));
        assertEquals(byte[][].class, ObjectHelper.loadClass("byte[][]"));
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Handle {
    }

    public interface Handler<T> {
        void handle(T value);
    }

    public static class StringHandler implements Handler<String> {
        @Handle
        @Override
        public void handle(String value) {
            // noop
        }
    }

    public static class Base {
        @Handle
        public void foo() {
            // noop
        }
    }

    public static class Sub extends Base {
        @Handle
        @Override
        public void foo() {
            // noop
        }
    }

    @Test
    public void testFindMethodsWithAnnotation() {
        List<Method> methods = AnnotationHelper.findMethodsWithAnnotation(StringHandler.class, Handle.class);
        assertEquals(1, methods.size());
        assertEquals(String.class, methods.get(0).getParameterTypes()[0]);

        methods = AnnotationHelper.findMethodsWithAnnotation(Sub.class, Handle.class);
        assertEquals(1, methods.size());
        assertEquals(Sub.class, methods.get(0).getDeclaringClass());
    }

    private static class RejectableTask implements Runnable, Rejectable {
        private final AtomicBoolean rejected = new AtomicBoolean();

        @Override
        public void run() {
            // noop
        }

        @Override
        public void reject() {
            rejected.set(true);
        }
    }

    @Test
    public void testBlockPolicyRejectsWhenShutdown() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                ThreadPoolRejectedPolicy.Block.asRejectedExecutionHandler());
        executor.shutdown();

        RejectableTask task = new RejectableTask();
        executor.execute(task);
        assertTrue(task.rejected.get());
    }
}
