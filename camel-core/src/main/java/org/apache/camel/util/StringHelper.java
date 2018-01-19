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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import static org.apache.camel.util.StringQuoteHelper.doubleQuote;

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
     * @param s String to be sanitized.
     * @return sanitized version of <code>s</code>.
     * @throws NullPointerException if <code>s</code> is <code>null</code>.
     */
    public static String sanitize(String s) {
        return s
            .replace(':', '-')
            .replace('_', '-')
            .replace('.', '-')
            .replace('/', '-')
            .replace('\\', '-');
    }

    /**
     * Counts the number of times the given char is in the string
     *
     * @param s  the string
     * @param ch the char
     * @return number of times char is located in the string
     */
    public static int countChar(String s, char ch) {
        if (ObjectHelper.isEmpty(s)) {
            return 0;
        }

        int matches = 0;
        for (int i = 0; i < s.length(); i++) {
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
     * @param s the string
     * @param maxLength the maximum length of the returned string
     * @return s if the length of s is less than maxLength or the first maxLength characters of s
     * @deprecated use {@link #limitLength(String, int)}
     */
    @Deprecated
    public static String limitLenght(String s, int maxLength) {
        return limitLength(s, maxLength);
    }

    /**
     * Limits the length of a string
     *
     * @param s the string
     * @param maxLength the maximum length of the returned string
     * @return s if the length of s is less than maxLength or the first maxLength characters of s
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
     * @param s  the string
     * @return the string without quotes (single and double)
     */
    public static String removeQuotes(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        s = replaceAll(s, "'", "");
        s = replaceAll(s, "\"", "");
        return s;
    }

    /**
     * Removes all leading and ending quotes (single and double) from the string
     *
     * @param s  the string
     * @return the string without leading and ending quotes (single and double)
     */
    public static String removeLeadingAndEndingQuotes(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        String copy = s.trim();
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
     * @param s the string
     * @return <tt>true</tt> if the string starts and ends with either single or double quotes.
     */
    public static boolean isQuoted(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return false;
        }

        if (s.startsWith("'") && s.endsWith("'")) {
            return true;
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return true;
        }

        return false;
    }

    /**
     * Encodes the text into safe XML by replacing < > and & with XML tokens
     *
     * @param text  the text
     * @return the encoded text
     */
    public static String xmlEncode(String text) {
        if (text == null) {
            return "";
        }
        // must replace amp first, so we dont replace &lt; to amp later
        text = replaceAll(text, "&", "&amp;");
        text = replaceAll(text, "\"", "&quot;");
        text = replaceAll(text, "<", "&lt;");
        text = replaceAll(text, ">", "&gt;");
        return text;
    }

    /**
     * Determines if the string has at least one letter in upper case
     * @param text the text
     * @return <tt>true</tt> if at least one letter is upper case, <tt>false</tt> otherwise
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
        boolean result = false;
        if (text != null) {
            String[] split = text.split("\\.");
            if (split.length > 0) {
                String lastToken = split[split.length - 1];
                if (lastToken.length() > 0) {
                    result = Character.isUpperCase(lastToken.charAt(0));
                }
            }
        }
        return result;
    }

    /**
     * Does the expression have the language start token?
     *
     * @param expression the expression
     * @param language the name of the language, such as simple
     * @return <tt>true</tt> if the expression contains the start token, <tt>false</tt> otherwise
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
     * Replaces all the from tokens in the given input string.
     * <p/>
     * This implementation is not recursive, not does it check for tokens in the replacement string.
     *
     * @param input  the input string
     * @param from   the from string, must <b>not</b> be <tt>null</tt> or empty
     * @param to     the replacement string, must <b>not</b> be empty
     * @return the replaced string, or the input string if no replacement was needed
     * @throws IllegalArgumentException if the input arguments is invalid
     */
    public static String replaceAll(String input, String from, String to) {
        if (ObjectHelper.isEmpty(input)) {
            return input;
        }
        if (from == null) {
            throw new IllegalArgumentException("from cannot be null");
        }
        if (to == null) {
            // to can be empty, so only check for null
            throw new IllegalArgumentException("to cannot be null");
        }

        // fast check if there is any from at all
        if (!input.contains(from)) {
            return input;
        }

        final int len = from.length();
        final int max = input.length();
        StringBuilder sb = new StringBuilder(max);
        for (int i = 0; i < max;) {
            if (i + len <= max) {
                String token = input.substring(i, i + len);
                if (from.equals(token)) {
                    sb.append(to);
                    // fast forward
                    i = i + len;
                    continue;
                }
            }

            // append single char
            sb.append(input.charAt(i));
            // forward to next
            i++;
        }
        return sb.toString();
    }

    /**
     * Creates a json tuple with the given name/value pair.
     *
     * @param name  the name
     * @param value the value
     * @param isMap whether the tuple should be map
     * @return the json
     */
    public static String toJson(String name, String value, boolean isMap) {
        if (isMap) {
            return "{ " + doubleQuote(name) + ": " + doubleQuote(value) + " }";
        } else {
            return doubleQuote(name) + ": " + doubleQuote(value);
        }
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param value  the string to test
     * @param name   the key that resolved the value
     * @return the passed {@code value} as is
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
     * @param value  the string to test
     * @param on     additional description to indicate where this problem occurred (appended as toString())
     * @param name   the key that resolved the value
     * @return the passed {@code value} as is
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
        String rc[] = new String[count];
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

    /**
     * Removes any starting characters on the given text which match the given
     * character
     *
     * @param text the string
     * @param ch the initial characters to remove
     * @return either the original string or the new substring
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
     * @param text  the string
     * @return the string capitalized (upper case first character)
     */
    public static String capitalize(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        String answer = text.substring(0, 1).toUpperCase(Locale.ENGLISH);
        if (length > 1) {
            answer += text.substring(1, length);
        }
        return answer;
    }

    /**
     * Returns the string after the given token
     *
     * @param text  the text
     * @param after the token
     * @return the text after the token, or <tt>null</tt> if text does not contain the token
     */
    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    /**
     * Returns an object after the given token
     *
     * @param text  the text
     * @param after the token
     * @param mapper a mapping function to convert the string after the token to type T
     * @return an Optional describing the result of applying a mapping function to the text after the token.
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
     * Returns the string before the given token
     *
     * @param text the text
     * @param before the token
     * @return the text before the token, or <tt>null</tt> if text does not
     *         contain the token
     */
    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    /**
     * Returns an object before the given token
     *
     * @param text  the text
     * @param before the token
     * @param mapper a mapping function to convert the string before the token to type T
     * @return an Optional describing the result of applying a mapping function to the text before the token.
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
     * Returns the string between the given tokens
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @return the text between the tokens, or <tt>null</tt> if text does not contain the tokens
     */
    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

    /**
     * Returns an object between the given token
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @param mapper a mapping function to convert the string between the token to type T
     * @return an Optional describing the result of applying a mapping function to the text between the token.
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
     * The number of token pairs must be evenly, eg there must be same number of before and after tokens, otherwise <tt>null</tt> is returned
     * <p/>
     * This implementation skips matching when the text is either single or double quoted.
     * For example:
     * <tt>${body.matches("foo('bar')")</tt>
     * Will not match the parenthesis from the quoted text.
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @return the text between the outer most tokens, or <tt>null</tt> if text does not contain the tokens
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
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @param mapper a mapping function to convert the string between the most outer pair of tokens to type T
     * @return an Optional describing the result of applying a mapping function to the text between the most outer pair of tokens.
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
     * Especially from Spring DSL people can have \n \t or other characters that otherwise
     * would result in ClassNotFoundException
     *
     * @param name the class name
     * @return normalized classname that can be load by a class loader.
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
     * @param oldText  the old text
     * @param newText  the new text
     * @return a list of line numbers that are changed in the new text
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
     * Removes the leading and trailing whitespace and if the resulting
     * string is empty returns {@code null}. Examples:
     * <p>
     * Examples:
     * <blockquote><pre>
     * trimToNull("abc") -> "abc"
     * trimToNull(" abc") -> "abc"
     * trimToNull(" abc ") -> "abc"
     * trimToNull(" ") -> null
     * trimToNull("") -> null
     * </pre></blockquote>
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
     * @param src  is the source string to be checked
     * @param what is the string which will be looked up in the src argument 
     * @return true/false
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
     * @param bytes number of bytes
     * @return human readable output
     */
    public static String humanReadableBytes(long bytes) {
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}
