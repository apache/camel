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
package org.apache.camel.component.file;

import java.io.File;
import java.util.Comparator;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.util.ObjectHelper;

/**
 * Default file sorter.
 *
 * @version $Revision$
 */
public final class DefaultFileSorter {

    private DefaultFileSorter() {
    }

    /**
     * Returns a new sory by name
     */
    public static Comparator<File> sortByName() {
        return new Comparator<File>() {
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };
    }

    /**
     * Returns a new sory by path name
     */
    public static Comparator<File> sortByPathName() {
        return new Comparator<File>() {
            public int compare(File o1, File o2) {
                return o1.getPath().compareTo(o2.getPath());
            }
        };
    }

    /**
     * Returns a new sory by last modified (newest first)
     */
    public static Comparator<File> sortByLastModified() {
        return new Comparator<File>() {
            public int compare(File o1, File o2) {
                long delta = o1.lastModified() - o2.lastModified();
                if (delta == 0) {
                    return 0;
                }
                return delta > 0 ? 1 : -1;
            }
        };
    }

    /**
     * Returns a new sory by file size (smallest first)
     */
    public static Comparator<File> sortBySize() {
        return new Comparator<File>() {
            public int compare(File o1, File o2) {
                long delta = o1.length() - o2.length();
                if (delta == 0) {
                    return 0;
                } 
                return delta > 0 ? 1 : -1;
            }
        };
    }

    public static Comparator<FileExchange> sortByFileLanguage(final String expression, final boolean reverse) {
        return sortByFileLanguage(expression, reverse, null);
    }

    public static Comparator<FileExchange> sortByFileLanguage(final String expression, final boolean reverse,
                                                              final Comparator<FileExchange> nested) {
        return new Comparator<FileExchange>() {
            public int compare(FileExchange o1, FileExchange o2) {
                final Expression exp = FileLanguage.file(expression);
                Object result1 = exp.evaluate(o1);
                Object result2 = exp.evaluate(o2);
                int answer = ObjectHelper.compare(result1, result2);
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
