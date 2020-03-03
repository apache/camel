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
package org.apache.camel.component.ssh;

import java.util.Optional;
import java.util.function.Function;

import org.apache.camel.util.StringHelper;

public final class SshShellOutputStringHelper {

    private SshShellOutputStringHelper() {
        // empty const
    }

   /**
     * Returns the string before the given token
     * If this token is repeating, than return all text
     * before the last token
     *
     * @param text the text
     * @param before the token which is expected to be repeated
     * @return the text before the last token, or <tt>null</tt> if text does not
     *         contain the token
     */
    public static String beforeLast(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.lastIndexOf(before));
    }
    
    
    /**
     * Returns an object before the given last token
     *
     * @param text  the text
     * @param beforeLast the last token
     * @param mapper a mapping function to convert the string before the token to type T
     * @return an Optional describing the result of applying a mapping function to the text before the token.
     */
    public static <T> Optional<T> beforeLast(String text, String beforeLast, Function<String, T> mapper) {
        String result = beforeLast(text, beforeLast);
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
     * @param after is the starting token to skip the text before that.
     * @param beforeLast the last token
     * @return the text between the tokens, or <tt>null</tt> if text does not contain the tokens
     */
    public static String betweenBeforeLast(String text, String after, String beforeLast) {
        text = StringHelper.after(text, after);
        if (text == null) {
            return null;
        }
        return beforeLast(text, beforeLast);
    }
    

    /**
     * Returns an object between the given token
     *
     * @param text  the text
     * @param after the before last token
     * @param before the after token
     * @param mapper a mapping function to convert the string between the token to type T
     * @return an Optional describing the result of applying a mapping function to the text between the token.
     */
    public static <T> Optional<T> betweenBeforeLast(String text, String after, String before, Function<String, T> mapper) {
        String result = betweenBeforeLast(text, after, before);
        if (result == null) {
            return Optional.empty();            
        } else {
            return Optional.ofNullable(mapper.apply(result));
        }
    }
}
