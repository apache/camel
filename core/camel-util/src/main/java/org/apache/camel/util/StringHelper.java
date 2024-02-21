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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Helper methods for working with Strings.
 */
public final class StringHelper {

    /**
     * Constructor of utility class should be private.
     */
    private StringHelper() {
    }

    /**
     * Ensures that <code>s</code> is friendly for a URL or file system.
     *
     * @param  s                    String to be sanitized.
     * @return                      sanitized version of <code>s</code>.
     * @throws NullPointerException if <code>s</code> is <code>null</code>.
     */
    public static String sanitize(final String s) {
        return s.replace(':', '-')
                .replace('_', '-')
                .replace('.', '-')
                .replace('/', '-')
                .replace('\\', '-');
    }

    /**
     * Remove carriage return and line feeds from a String, replacing them with an empty String.
     *
     * @param  s                    String to be sanitized of carriage return / line feed characters
     * @return                      sanitized version of <code>s</code>.
     * @throws NullPointerException if <code>s</code> is <code>null</code>.
     */
    public static String removeCRLF(String s) {
        return s
                .replace("\r", "")
                .replace("\n", "");
    }

    /**
     * Counts the number of times the given char is in the string
     *
     * @param  s  the string
     * @param  ch the char
     * @return    number of times char is located in the string
     */
    public static int countChar(String s, char ch) {
        return countChar(s, ch, -1);
    }

    /**
     * Counts the number of times the given char is in the string
     *
     * @param  s   the string
     * @param  ch  the char
     * @param  end end index
     * @return     number of times char is located in the string
     */
    public static int countChar(String s, char ch, int end) {
        if (s == null || s.isEmpty()) {
            return 0;
        }

        int matches = 0;
        int len = end < 0 ? s.length() : end;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (ch == c) {
                matches++;
            }
        }

        return matches;
    }

    /**
     * Limits the length of a string
     *
     * @param  s         the string
     * @param  maxLength the maximum length of the returned string
     * @return           s if the length of s is less than maxLength or the first maxLength characters of s
     */
    public static String limitLength(String s, int maxLength) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }

    /**
     * Removes all quotes (single and double) from the string
     *
     * @param  s the string
     * @return   the string without quotes (single and double)
     */
    public static String removeQuotes(final String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        return s.replace("'", "")
                .replace("\"", "");
    }

    /**
     * Removes all leading and ending quotes (single and double) from the string
     *
     * @param  s the string
     * @return   the string without leading and ending quotes (single and double)
     */
    public static String removeLeadingAndEndingQuotes(final String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        String copy = s.trim();
        if (copy.length() < 2) {
            return s;
        }
        if (copy.startsWith("'") && copy.endsWith("'")) {
            return copy.substring(1, copy.length() - 1);
        }
        if (copy.startsWith("\"") && copy.endsWith("\"")) {
            return copy.substring(1, copy.length() - 1);
        }

        // no quotes, so return as-is
        return s;
    }

    /**
     * Whether the string starts and ends with either single or double quotes.
     *
     * @param  s the string
     * @return   <tt>true</tt> if the string starts and ends with either single or double quotes.
     */
    public static boolean isQuoted(String s) {
        return isSingleQuoted(s) || isDoubleQuoted(s);
    }

    /**
     * Whether the string starts and ends with single quotes.
     *
     * @param  s the string
     * @return   <tt>true</tt> if the string starts and ends with single quotes.
     */
    public static boolean isSingleQuoted(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return false;
        }

        if (s.startsWith("'") && s.endsWith("'")) {
            return true;
        }

        return false;
    }

    /**
     * Whether the string starts and ends with double quotes.
     *
     * @param  s the string
     * @return   <tt>true</tt> if the string starts and ends with double quotes.
     */
    public static boolean isDoubleQuoted(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return false;
        }

        if (s.startsWith("\"") && s.endsWith("\"")) {
            return true;
        }

        return false;
    }

    /**
     * Encodes the text into safe XML by replacing < > and & with XML tokens
     *
     * @param  text the text
     * @return      the encoded text
     */
    public static String xmlEncode(final String text) {
        if (text == null) {
            return "";
        }
        // must replace amp first, so we dont replace &lt; to amp later
        return text.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Determines if the string has at least one letter in upper case
     *
     * @param  text the text
     * @return      <tt>true</tt> if at least one letter is upper case, <tt>false</tt> otherwise
     */
    public static boolean hasUpperCase(String text) {
        if (text == null) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isUpperCase(ch)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if the string is a fully qualified class name
     */
    public static boolean isClassName(String text) {
        if (text != null) {
            int lastIndexOf = text.lastIndexOf('.');
            if (lastIndexOf <= 0 || lastIndexOf == text.length()) {
                return false;
            }

            return Character.isUpperCase(text.charAt(lastIndexOf + 1));
        }

        return false;
    }

    /**
     * Does the expression have the language start token?
     *
     * @param  expression the expression
     * @param  language   the name of the language, such as simple
     * @return            <tt>true</tt> if the expression contains the start token, <tt>false</tt> otherwise
     */
    public static boolean hasStartToken(String expression, String language) {
        if (expression == null) {
            return false;
        }

        // for the simple language the expression start token could be "${"
        if ("simple".equalsIgnoreCase(language) && expression.contains("${")) {
            return true;
        }

        if (language != null && expression.contains("$" + language + "{")) {
            return true;
        }

        return false;
    }

    /**
     * Replaces the first from token in the given input string.
     * <p/>
     * This implementation is not recursive, not does it check for tokens in the replacement string.
     *
     * @param  input                    the input string
     * @param  from                     the from string, must <b>not</b> be <tt>null</tt> or empty
     * @param  to                       the replacement string, must <b>not</b> be empty
     * @return                          the replaced string, or the input string if no replacement was needed
     * @throws IllegalArgumentException if the input arguments is invalid
     */
    public static String replaceFirst(String input, String from, String to) {
        int pos = input.indexOf(from);
        if (pos != -1) {
            int len = from.length();
            return input.substring(0, pos) + to + input.substring(pos + len);
        } else {
            return input;
        }
    }

    /**
     * Creates a json tuple with the given name/value pair.
     *
     * @param  name  the name
     * @param  value the value
     * @param  isMap whether the tuple should be map
     * @return       the json
     */
    public static String toJson(String name, String value, boolean isMap) {
        if (isMap) {
            return "{ " + StringQuoteHelper.doubleQuote(name) + ": " + StringQuoteHelper.doubleQuote(value) + " }";
        } else {
            return StringQuoteHelper.doubleQuote(name) + ": " + StringQuoteHelper.doubleQuote(value);
        }
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param  value                    the string to test
     * @param  name                     the key that resolved the value
     * @return                          the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static String notEmpty(String value, String name) {
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(name + " must be specified and not empty");
        }

        return value;
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param  value                    the string to test
     * @param  on                       additional description to indicate where this problem occurred (appended as
     *                                  toString())
     * @param  name                     the key that resolved the value
     * @return                          the passed {@code value} as is
     * @throws IllegalArgumentException is thrown if assertion fails
     */
    public static String notEmpty(String value, String name, Object on) {
        if (on == null) {
            ObjectHelper.notNull(value, name);
        } else if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(name + " must be specified and not empty on: " + on);
        }

        return value;
    }

    public static String[] splitOnCharacter(String value, String needle, int count) {
        String[] rc = new String[count];
        rc[0] = value;
        for (int i = 1; i < count; i++) {
            String v = rc[i - 1];
            int p = v.indexOf(needle);
            if (p < 0) {
                return rc;
            }
            rc[i - 1] = v.substring(0, p);
            rc[i] = v.substring(p + 1);
        }
        return rc;
    }

    public static Iterator<String> splitOnCharacterAsIterator(String value, char needle, int count) {
        // skip leading and trailing needles
        int end = value.length() - 1;
        boolean skipStart = value.charAt(0) == needle;
        boolean skipEnd = value.charAt(end) == needle;
        if (skipStart && skipEnd) {
            value = value.substring(1, end);
            count = count - 2;
        } else if (skipStart) {
            value = value.substring(1);
            count = count - 1;
        } else if (skipEnd) {
            value = value.substring(0, end);
            count = count - 1;
        }

        final int size = count;
        final String text = value;

        return new Iterator<>() {
            int i;
            int pos;

            @Override
            public boolean hasNext() {
                return i < size;
            }

            @Override
            public String next() {
                if (i == size) {
                    throw new NoSuchElementException();
                }
                String answer;
                int end = text.indexOf(needle, pos);
                if (end != -1) {
                    answer = text.substring(pos, end);
                    pos = end + 1;
                } else {
                    answer = text.substring(pos);
                    // no more data
                    i = size;
                }
                return answer;
            }
        };
    }

    public static List<String> splitOnCharacterAsList(String value, char needle, int count) {
        // skip leading and trailing needles
        int end = value.length() - 1;
        boolean skipStart = value.charAt(0) == needle;
        boolean skipEnd = value.charAt(end) == needle;
        if (skipStart && skipEnd) {
            value = value.substring(1, end);
            count = count - 2;
        } else if (skipStart) {
            value = value.substring(1);
            count = count - 1;
        } else if (skipEnd) {
            value = value.substring(0, end);
            count = count - 1;
        }

        List<String> rc = new ArrayList<>(count);
        int pos = 0;
        for (int i = 0; i < count; i++) {
            end = value.indexOf(needle, pos);
            if (end != -1) {
                String part = value.substring(pos, end);
                pos = end + 1;
                rc.add(part);
            } else {
                rc.add(value.substring(pos));
                break;
            }
        }
        return rc;
    }

    /**
     * Removes any starting characters on the given text which match the given character
     *
     * @param  text the string
     * @param  ch   the initial characters to remove
     * @return      either the original string or the new substring
     */
    public static String removeStartingCharacters(String text, char ch) {
        int idx = 0;
        while (text.charAt(idx) == ch) {
            idx++;
        }
        if (idx > 0) {
            return text.substring(idx);
        }
        return text;
    }

    /**
     * Capitalize the string (upper case first character)
     *
     * @param  text the string
     * @return      the string capitalized (upper case first character)
     */
    public static String capitalize(String text) {
        return capitalize(text, false);
    }

    /**
     * Capitalize the string (upper case first character)
     *
     * @param  text            the string
     * @param  dashToCamelCase whether to also convert dash format into camel case (hello-great-world ->
     *                         helloGreatWorld)
     * @return                 the string capitalized (upper case first character)
     */
    public static String capitalize(final String text, boolean dashToCamelCase) {
        String ret = text;
        if (dashToCamelCase) {
            ret = dashToCamelCase(text);
        }
        if (ret == null) {
            return null;
        }

        final char[] chars = ret.toCharArray();

        // We are OK with the limitations of Character.toUpperCase. The symbols and ideographs
        // for which it does not return the capitalized value should not be used here (this is
        // mostly used to capitalize setters/getters)
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    /**
     * De-capitalize the string (lower case first character)
     *
     * @param  text the string
     * @return      the string decapitalized (lower case first character)
     */
    public static String decapitalize(final String text) {
        if (text == null) {
            return null;
        }

        final char[] chars = text.toCharArray();

        // We are OK with the limitations of Character.toLowerCase. The symbols and ideographs
        // for which it does not return the lower case value should not be used here (this is
        // mostly used to convert part of setters/getters to properties)
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Converts the string from dash format into camel case (hello-great-world -> helloGreatWorld)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String dashToCamelCase(final String text) {
        return dashToCamelCase(text, false);
    }

    /**
     * Converts the string from dash format into camel case (hello-great-world -> helloGreatWorld)
     *
     * @param  text              the string
     * @param  skipQuotedOrKeyed flag to skip converting within quoted or keyed text
     * @return                   the string camel cased
     */
    public static String dashToCamelCase(final String text, boolean skipQuotedOrKeyed) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        if (text.indexOf('-') == -1) {
            return text;
        }

        // there is at least 1 dash so the capacity can be shorter
        StringBuilder sb = new StringBuilder(length - 1);
        boolean upper = false;
        int singleQuotes = 0;
        int doubleQuotes = 0;
        boolean skip = false;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            // special for skip mode where we should keep text inside quotes or keys as-is
            if (skipQuotedOrKeyed) {
                if (c == ']') {
                    skip = false;
                } else if (c == '[') {
                    skip = true;
                } else if (c == '\'') {
                    singleQuotes++;
                } else if (c == '"') {
                    doubleQuotes++;
                }
                if (singleQuotes > 0) {
                    skip = singleQuotes % 2 == 1;
                }
                if (doubleQuotes > 0) {
                    skip = doubleQuotes % 2 == 1;
                }
                if (skip) {
                    sb.append(c);
                    continue;
                }
            }

            if (c == '-') {
                upper = true;
            } else {
                if (upper) {
                    c = Character.toUpperCase(c);
                }
                sb.append(c);
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * Returns the string after the given token
     *
     * @param  text  the text
     * @param  after the token
     * @return       the text after the token, or <tt>null</tt> if text does not contain the token
     */
    public static String after(String text, String after) {
        if (text == null) {
            return null;
        }
        int pos = text.indexOf(after);
        if (pos == -1) {
            return null;
        }
        return text.substring(pos + after.length());
    }

    /**
     * Returns the string after the given token, or the default value
     *
     * @param  text         the text
     * @param  after        the token
     * @param  defaultValue the value to return if text does not contain the token
     * @return              the text after the token, or the supplied defaultValue if text does not contain the token
     */
    public static String after(String text, String after, String defaultValue) {
        String answer = after(text, after);
        return answer != null ? answer : defaultValue;
    }

    /**
     * Returns an object after the given token
     *
     * @param  text   the text
     * @param  after  the token
     * @param  mapper a mapping function to convert the string after the token to type T
     * @return        an Optional describing the result of applying a mapping function to the text after the token.
     */
    public static <T> Optional<T> after(String text, String after, Function<String, T> mapper) {
        String result = after(text, after);
        if (result == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(mapper.apply(result));
        }
    }

    /**
     * Returns the string after the the last occurrence of the given token
     *
     * @param  text  the text
     * @param  after the token
     * @return       the text after the token, or <tt>null</tt> if text does not contain the token
     */
    public static String afterLast(String text, String after) {
        if (text == null) {
            return null;
        }
        int pos = text.lastIndexOf(after);
        if (pos == -1) {
            return null;
        }
        return text.substring(pos + after.length());
    }

    /**
     * Returns the string after the the last occurrence of the given token, or the default value
     *
     * @param  text         the text
     * @param  after        the token
     * @param  defaultValue the value to return if text does not contain the token
     * @return              the text after the token, or the supplied defaultValue if text does not contain the token
     */
    public static String afterLast(String text, String after, String defaultValue) {
        String answer = afterLast(text, after);
        return answer != null ? answer : defaultValue;
    }

    /**
     * Returns the string before the given token
     *
     * @param  text   the text
     * @param  before the token
     * @return        the text before the token, or <tt>null</tt> if text does not contain the token
     */
    public static String before(String text, String before) {
        if (text == null) {
            return null;
        }
        int pos = text.indexOf(before);
        return pos == -1 ? null : text.substring(0, pos);
    }

    /**
     * Returns the string before the given token, or the default value
     *
     * @param  text         the text
     * @param  before       the token
     * @param  defaultValue the value to return if text does not contain the token
     * @return              the text before the token, or the supplied defaultValue if text does not contain the token
     */
    public static String before(String text, String before, String defaultValue) {
        if (text == null) {
            return defaultValue;
        }
        int pos = text.indexOf(before);
        return pos == -1 ? defaultValue : text.substring(0, pos);
    }

    /**
     * Returns the string before the given token, or the default value
     *
     * @param  text         the text
     * @param  before       the token
     * @param  defaultValue the value to return if text does not contain the token
     * @return              the text before the token, or the supplied defaultValue if text does not contain the token
     */
    public static String before(String text, char before, String defaultValue) {
        if (text == null) {
            return defaultValue;
        }
        int pos = text.indexOf(before);
        return pos == -1 ? defaultValue : text.substring(0, pos);
    }

    /**
     * Returns an object before the given token
     *
     * @param  text   the text
     * @param  before the token
     * @param  mapper a mapping function to convert the string before the token to type T
     * @return        an Optional describing the result of applying a mapping function to the text before the token.
     */
    public static <T> Optional<T> before(String text, String before, Function<String, T> mapper) {
        String result = before(text, before);
        if (result == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(mapper.apply(result));
        }
    }

    /**
     * Returns the string before the last occurrence of the given token
     *
     * @param  text   the text
     * @param  before the token
     * @return        the text before the token, or <tt>null</tt> if text does not contain the token
     */
    public static String beforeLast(String text, String before) {
        if (text == null) {
            return null;
        }
        int pos = text.lastIndexOf(before);
        return pos == -1 ? null : text.substring(0, pos);
    }

    /**
     * Returns the string before the last occurrence of the given token, or the default value
     *
     * @param  text         the text
     * @param  before       the token
     * @param  defaultValue the value to return if text does not contain the token
     * @return              the text before the token, or the supplied defaultValue if text does not contain the token
     */
    public static String beforeLast(String text, String before, String defaultValue) {
        String answer = beforeLast(text, before);
        return answer != null ? answer : defaultValue;
    }

    /**
     * Returns the string between the given tokens
     *
     * @param  text   the text
     * @param  after  the before token
     * @param  before the after token
     * @return        the text between the tokens, or <tt>null</tt> if text does not contain the tokens
     */
    public static String between(final String text, String after, String before) {
        String ret = after(text, after);
        if (ret == null) {
            return null;
        }
        return before(ret, before);
    }

    /**
     * Returns an object between the given token
     *
     * @param  text   the text
     * @param  after  the before token
     * @param  before the after token
     * @param  mapper a mapping function to convert the string between the token to type T
     * @return        an Optional describing the result of applying a mapping function to the text between the token.
     */
    public static <T> Optional<T> between(String text, String after, String before, Function<String, T> mapper) {
        String result = between(text, after, before);
        if (result == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(mapper.apply(result));
        }
    }

    /**
     * Returns the string between the most outer pair of tokens
     * <p/>
     * The number of token pairs must be evenly, eg there must be same number of before and after tokens, otherwise
     * <tt>null</tt> is returned
     * <p/>
     * This implementation skips matching when the text is either single or double quoted. For example:
     * <tt>${body.matches("foo('bar')")</tt> Will not match the parenthesis from the quoted text.
     *
     * @param  text   the text
     * @param  after  the before token
     * @param  before the after token
     * @return        the text between the outer most tokens, or <tt>null</tt> if text does not contain the tokens
     */
    public static String betweenOuterPair(String text, char before, char after) {
        if (text == null) {
            return null;
        }

        int pos = -1;
        int pos2 = -1;
        int count = 0;
        int count2 = 0;

        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!doubleQuoted && ch == '\'') {
                singleQuoted = !singleQuoted;
            } else if (!singleQuoted && ch == '\"') {
                doubleQuoted = !doubleQuoted;
            }
            if (singleQuoted || doubleQuoted) {
                continue;
            }

            if (ch == before) {
                count++;
            } else if (ch == after) {
                count2++;
            }

            if (ch == before && pos == -1) {
                pos = i;
            } else if (ch == after) {
                pos2 = i;
            }
        }

        if (pos == -1 || pos2 == -1) {
            return null;
        }

        // must be even paris
        if (count != count2) {
            return null;
        }

        return text.substring(pos + 1, pos2);
    }

    /**
     * Returns an object between the most outer pair of tokens
     *
     * @param  text   the text
     * @param  after  the before token
     * @param  before the after token
     * @param  mapper a mapping function to convert the string between the most outer pair of tokens to type T
     * @return        an Optional describing the result of applying a mapping function to the text between the most
     *                outer pair of tokens.
     */
    public static <T> Optional<T> betweenOuterPair(String text, char before, char after, Function<String, T> mapper) {
        String result = betweenOuterPair(text, before, after);
        if (result == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(mapper.apply(result));
        }
    }

    /**
     * Returns true if the given name is a valid java identifier
     */
    public static boolean isJavaIdentifier(String name) {
        if (name == null) {
            return false;
        }
        int size = name.length();
        if (size < 1) {
            return false;
        }
        if (Character.isJavaIdentifierStart(name.charAt(0))) {
            for (int i = 1; i < size; i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Cleans the string to a pure Java identifier so we can use it for loading class names.
     * <p/>
     * Especially from Spring DSL people can have \n \t or other characters that otherwise would result in
     * ClassNotFoundException
     *
     * @param  name the class name
     * @return      normalized classname that can be load by a class loader.
     */
    public static String normalizeClassName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            if (ch == '.' || ch == '[' || ch == ']' || ch == '-' || Character.isJavaIdentifierPart(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Compares old and new text content and report back which lines are changed
     *
     * @param  oldText the old text
     * @param  newText the new text
     * @return         a list of line numbers that are changed in the new text
     */
    public static List<Integer> changedLines(String oldText, String newText) {
        if (oldText == null || oldText.equals(newText)) {
            return Collections.emptyList();
        }

        List<Integer> changed = new ArrayList<>();

        String[] oldLines = oldText.split("\n");
        String[] newLines = newText.split("\n");

        for (int i = 0; i < newLines.length; i++) {
            String newLine = newLines[i];
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            if (oldLine == null) {
                changed.add(i);
            } else if (!newLine.equals(oldLine)) {
                changed.add(i);
            }
        }

        return changed;
    }

    /**
     * Removes the leading and trailing whitespace and if the resulting string is empty returns {@code null}. Examples:
     * <p>
     * Examples: <blockquote>
     *
     * <pre>
     * trimToNull("abc") -> "abc"
     * trimToNull(" abc") -> "abc"
     * trimToNull(" abc ") -> "abc"
     * trimToNull(" ") -> null
     * trimToNull("") -> null
     * </pre>
     *
     * </blockquote>
     */
    public static String trimToNull(final String given) {
        if (given == null) {
            return null;
        }

        final String trimmed = given.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }

    /**
     * Checks if the src string contains what
     *
     * @param  src  is the source string to be checked
     * @param  what is the string which will be looked up in the src argument
     * @return      true/false
     */
    public static boolean containsIgnoreCase(String src, String what) {
        if (src == null || what == null) {
            return false;
        }

        final int length = what.length();
        if (length == 0) {
            return true; // Empty string is contained
        }

        final char firstLo = Character.toLowerCase(what.charAt(0));
        final char firstUp = Character.toUpperCase(what.charAt(0));

        for (int i = src.length() - length; i >= 0; i--) {
            // Quick check before calling the more expensive regionMatches() method:
            final char ch = src.charAt(i);
            if (ch != firstLo && ch != firstUp) {
                continue;
            }

            if (src.regionMatches(true, i, what, 0, length)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Outputs the bytes in human readable format in units of KB,MB,GB etc.
     *
     * @param  locale The locale to apply during formatting. If l is {@code null} then no localization is applied.
     * @param  bytes  number of bytes
     * @return        human readable output
     * @see           java.lang.String#format(Locale, String, Object...)
     */
    public static String humanReadableBytes(Locale locale, long bytes) {
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = String.valueOf("KMGTPE".charAt(exp - 1));
        return String.format(locale, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /**
     * Outputs the bytes in human readable format in units of KB,MB,GB etc.
     *
     * The locale always used is the one returned by {@link java.util.Locale#getDefault()}.
     *
     * @param  bytes number of bytes
     * @return       human readable output
     * @see          org.apache.camel.util.StringHelper#humanReadableBytes(Locale, long)
     */
    public static String humanReadableBytes(long bytes) {
        return humanReadableBytes(Locale.getDefault(), bytes);
    }

    /**
     * Check for string pattern matching with a number of strategies in the following order:
     *
     * - equals - null pattern always matches - * always matches - Ant style matching - Regexp
     *
     * @param  pattern the pattern
     * @param  target  the string to test
     * @return         true if target matches the pattern
     */
    public static boolean matches(String pattern, String target) {
        if (Objects.equals(pattern, target)) {
            return true;
        }

        if (Objects.isNull(pattern)) {
            return true;
        }

        if (Objects.equals("*", pattern)) {
            return true;
        }

        if (AntPathMatcher.INSTANCE.match(pattern, target)) {
            return true;
        }

        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(target);

        return m.matches();
    }

    /**
     * Converts the string from camel case into dash format (helloGreatWorld -> hello-great-world)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String camelCaseToDash(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder answer = new StringBuilder();

        Character prev = null;
        Character next = null;
        char[] arr = text.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (i < arr.length - 1) {
                next = arr[i + 1];
            } else {
                next = null;
            }
            if (ch == '-' || ch == '_') {
                answer.append("-");
            } else if (Character.isUpperCase(ch) && prev != null && !Character.isUpperCase(prev)) {
                applyDashPrefix(prev, answer, ch);
            } else if (Character.isUpperCase(ch) && prev != null && next != null && Character.isLowerCase(next)) {
                applyDashPrefix(prev, answer, ch);
            } else {
                answer.append(Character.toLowerCase(ch));
            }
            prev = ch;
        }

        return answer.toString();
    }

    private static void applyDashPrefix(Character prev, StringBuilder answer, char ch) {
        if (prev != '-' && prev != '_') {
            answer.append("-");
        }
        answer.append(Character.toLowerCase(ch));
    }

    /**
     * Does the string starts with the given prefix (ignore case).
     *
     * @param text   the string
     * @param prefix the prefix
     */
    public static boolean startsWithIgnoreCase(String text, String prefix) {
        if (text != null && prefix != null) {
            return prefix.length() <= text.length() && text.regionMatches(true, 0, prefix, 0, prefix.length());
        } else {
            return text == null && prefix == null;
        }
    }

    /**
     * Converts the value to an enum constant value that is in the form of upper cased with underscore.
     */
    public static String asEnumConstantValue(final String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String ret = StringHelper.camelCaseToDash(value);
        // replace double dashes
        ret = ret.replaceAll("-+", "-");
        // replace dash with underscore and upper case
        ret = ret.replace('-', '_').toUpperCase(Locale.ENGLISH);
        return ret;
    }

    /**
     * Split the text on words, eg hello/world => becomes array with hello in index 0, and world in index 1.
     */
    public static String[] splitWords(String text) {
        return text.split("[\\W]+");
    }

    /**
     * Creates a stream from the given input sequence around matches of the regex
     *
     * @param  text  the input
     * @param  regex the expression used to split the input
     * @return       the stream of strings computed by splitting the input with the given regex
     */
    public static Stream<String> splitAsStream(CharSequence text, String regex) {
        if (text == null || regex == null) {
            return Stream.empty();
        }

        return Pattern.compile(regex).splitAsStream(text);
    }

    /**
     * Returns the occurrence of a search string in to a string.
     *
     * @param  text   the text
     * @param  search the string to search
     * @return        an integer reporting the number of occurrence of the searched string in to the text
     */
    public static int countOccurrence(String text, String search) {
        int lastIndex = 0;
        int count = 0;
        while (lastIndex != -1) {
            lastIndex = text.indexOf(search, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += search.length();
            }
        }
        return count;
    }

    /**
     * Replaces a string in to a text starting from his second occurrence.
     *
     * @param  text        the text
     * @param  search      the string to search
     * @param  replacement the replacement for the string
     * @return             the string with the replacement
     */
    public static String replaceFromSecondOccurrence(String text, String search, String replacement) {
        int index = text.indexOf(search);
        boolean replace = false;

        while (index != -1) {
            String tempString = text.substring(index);
            if (replace) {
                tempString = tempString.replaceFirst(search, replacement);
                text = text.substring(0, index) + tempString;
                replace = false;
            } else {
                replace = true;
            }
            index = text.indexOf(search, index + 1);
        }
        return text;
    }

    /**
     * Pad the string with leading spaces
     *
     * @param level level (2 blanks per level)
     */
    public static String padString(int level) {
        return padString(level, 2);
    }

    /**
     * Pad the string with leading spaces
     *
     * @param level  level
     * @param blanks number of blanks per level
     */
    public static String padString(int level, int blanks) {
        if (level == 0) {
            return "";
        } else {
            return " ".repeat(level * blanks);
        }
    }

    /**
     * Fills the string with repeating chars
     *
     * @param ch    the char
     * @param count number of chars
     */
    public static String fillChars(char ch, int count) {
        if (count <= 0) {
            return "";
        } else {
            return Character.toString(ch).repeat(count);
        }
    }

    public static boolean isDigit(String s) {
        for (char ch : s.toCharArray()) {
            if (!Character.isDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    public static String bytesToHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

}
