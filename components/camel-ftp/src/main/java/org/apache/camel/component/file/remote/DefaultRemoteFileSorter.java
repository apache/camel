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
package org.apache.camel.component.file.remote;

import java.util.Comparator;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.util.ObjectHelper;

/**
 * Default remote file sorter.
 */
public final class DefaultRemoteFileSorter {

    private DefaultRemoteFileSorter() {
    }

    /**
     * Returns a new sort by name
     */
    public static Comparator<RemoteFile> sortByName(final boolean reverse) {
        return new Comparator<RemoteFile>() {
            public int compare(RemoteFile o1, RemoteFile o2) {
                int answer = o1.getFileName().compareTo(o2.getFileName());
                return reverse ? -1 * answer : answer;
            }
        };
    }

    /**
     * Returns a new sort by path name
     */
    public static Comparator<RemoteFile> sortByPathName(final boolean reverse) {
        return new Comparator<RemoteFile>() {
            public int compare(RemoteFile o1, RemoteFile o2) {
                int answer = o1.getParent().compareTo(o2.getParent());
                return reverse ? -1 * answer : answer;
            }
        };
    }

    /**
     * Returns a new sort by last modified (newest first)
     */
    public static Comparator<RemoteFile> sortByLastModified(final boolean reverse) {
        return new Comparator<RemoteFile>() {
            public int compare(RemoteFile o1, RemoteFile o2) {
                long delta = o1.getLastModified() - o2.getLastModified();
                if (delta == 0) {
                    return 0;
                }
                int answer = delta > 0 ? 1 : -1;
                return reverse ? -1 * answer : answer;
            }
        };
    }

    /**
     * Returns a new sort by file size (smallest first)
     */
    public static Comparator<RemoteFile> sortBySize(final boolean reverse) {
        return new Comparator<RemoteFile>() {
            public int compare(RemoteFile o1, RemoteFile o2) {
                long delta = o1.getFileLength() - o2.getFileLength();
                if (delta == 0) {
                    return 0;
                }
                int answer = delta > 0 ? 1 : -1;
                return reverse ? -1 * answer : answer;
            }
        };
    }

    /**
     * Returns a new sory by file language expression
     *
     * @param expression the file language expression
     * @param reverse    true to reverse order
     * @return the comparator
     */
    public static Comparator<RemoteFileExchange> sortByFileLanguage(final String expression, final boolean reverse) {
        return sortByFileLanguage(expression, reverse, false, null);
    }

    /**
     * Returns a new sory by file language expression
     *
     * @param expression the file language expression
     * @param reverse    true to reverse order
     * @param ignoreCase ignore case if comparing strings
     * @return the comparator
     */
    public static Comparator<RemoteFileExchange> sortByFileLanguage(final String expression, final boolean reverse,
                                                                    final boolean ignoreCase) {
        return sortByFileLanguage(expression, reverse, ignoreCase, null);
    }

    /**
     * Returns a new sory by file language expression
     *
     * @param expression the file language expression
     * @param reverse    true to reverse order
     * @param ignoreCase ignore case if comparing strings
     * @param nested     nested comparator for sub group sorting, can be null
     * @return the comparator
     */
    public static Comparator<RemoteFileExchange> sortByFileLanguage(final String expression, final boolean reverse,
                                                                    final boolean ignoreCase, final Comparator<RemoteFileExchange> nested) {
        return new Comparator<RemoteFileExchange>() {
            public int compare(RemoteFileExchange o1, RemoteFileExchange o2) {
                final Expression exp = FileLanguage.file(expression);
                Object result1 = exp.evaluate(o1, Object.class);
                Object result2 = exp.evaluate(o2, Object.class);
                int answer = ObjectHelper.compare(result1, result2, ignoreCase);
                // if equal then sub sort by nested comparator
                if (answer == 0 && nested != null) {
                    answer = nested.compare(o1, o2);
                }
                return reverse ? -1 * answer : answer;
            }

            public String toString() {
                return expression + (nested != null ? ";" + nested.toString() : "");
            }
        };
    }

}
