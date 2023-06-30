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
package org.apache.camel.util.json;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Jsoner provides JSON utilities for escaping strings to be JSON compatible, thread safe parsing (RFC 4627) JSON
 * strings, and serializing data to strings in JSON format.
 */
public final class Jsoner {
    /**
     * Flags to tweak the behavior of the primary deserialization method.
     */
    private enum DeserializationOptions {
        /**
         * Whether a multiple JSON values can be deserialized as a root element.
         */
        ALLOW_CONCATENATED_JSON_VALUES,
        /**
         * Whether a JsonArray can be deserialized as a root element.
         */
        ALLOW_JSON_ARRAYS,
        /**
         * Whether a boolean, null, Number, or String can be deserialized as a root element.
         */
        ALLOW_JSON_DATA,
        /**
         * Whether a JsonObject can be deserialized as a root element.
         */
        ALLOW_JSON_OBJECTS;
    }

    /**
     * Flags to tweak the behavior of the primary serialization method.
     */
    private enum SerializationOptions {
        /**
         * Instead of aborting serialization on non-JSON values that are Enums it will continue serialization with the
         * Enums' "${PACKAGE}.${DECLARING_CLASS}.${NAME}".
         *
         * @see Enum
         */
        ALLOW_FULLY_QUALIFIED_ENUMERATIONS,
        /**
         * Instead of aborting serialization on non-JSON values it will continue serialization by serializing the
         * non-JSON value directly into the now invalid JSON. Be mindful that invalid JSON will not successfully
         * deserialize.
         */
        ALLOW_INVALIDS,
        /**
         * Instead of aborting serialization on non-JSON values it will do nothing and continue serialization by
         * serializing the non-JSON value directly into the now invalid JSON. Be mindful that invalid JSON will not
         * successfully deserialize.
         */
        ALLOW_INVALIDS_NOOP,
        /**
         * Instead of aborting serialization on non-JSON values that implement Jsonable it will continue serialization
         * by deferring serialization to the Jsonable.
         *
         * @see Jsonable
         */
        ALLOW_JSONABLES,
        /**
         * Instead of aborting serialization on non-JSON values it will continue serialization by using reflection to
         * best describe the value as a JsonObject.
         */
        ALLOW_UNDEFINEDS;
    }

    /**
     * The possible States of a JSON deserializer.
     */
    private enum States {
        /**
         * Post-parsing state.
         */
        DONE,
        /**
         * Pre-parsing state.
         */
        INITIAL,
        /**
         * Parsing error, ParsingException should be thrown.
         */
        PARSED_ERROR,
        PARSING_ARRAY,
        /**
         * Parsing a key-value pair inside of an object.
         */
        PARSING_ENTRY,
        PARSING_OBJECT;
    }

    private Jsoner() {
        /* Keeping it classy. */
    }

    /**
     * Deserializes a readable stream according to the RFC 4627 JSON specification.
     *
     * @param  readableDeserializable   representing content to be deserialized as JSON.
     * @return                          either a boolean, null, Number, String, JsonObject, or JsonArray that best
     *                                  represents the deserializable.
     * @throws DeserializationException if an unexpected token is encountered in the deserializable. To recover from a
     *                                  DeserializationException: fix the deserializable to no longer have an unexpected
     *                                  token and try again.
     * @throws IOException              if the underlying reader encounters an I/O error. Ensure the reader is properly
     *                                  instantiated, isn't closed, or that it is ready before trying again.
     */
    public static Object deserialize(final Reader readableDeserializable) throws DeserializationException, IOException {
        return Jsoner.deserialize(readableDeserializable,
                EnumSet.of(DeserializationOptions.ALLOW_JSON_ARRAYS, DeserializationOptions.ALLOW_JSON_OBJECTS,
                        DeserializationOptions.ALLOW_JSON_DATA))
                .get(0);
    }

    /**
     * Deserialize a stream with all deserialized JSON values are wrapped in a JsonArray.
     *
     * @param  deserializable           representing content to be deserialized as JSON.
     * @param  flags                    representing the allowances and restrictions on deserialization.
     * @return                          the allowable object best represented by the deserializable.
     * @throws DeserializationException if a disallowed or unexpected token is encountered in the deserializable. To
     *                                  recover from a DeserializationException: fix the deserializable to no longer
     *                                  have a disallowed or unexpected token and try again.
     * @throws IOException              if the underlying reader encounters an I/O error. Ensure the reader is properly
     *                                  instantiated, isn't closed, or that it is ready before trying again.
     */
    private static JsonArray deserialize(final Reader deserializable, final Set<DeserializationOptions> flags)
            throws DeserializationException, IOException {
        final Yylex lexer = new Yylex(deserializable);
        Yytoken token;
        States currentState;
        int returnCount = 1;
        final LinkedList<States> stateStack = new LinkedList<>();
        final LinkedList<Object> valueStack = new LinkedList<>();
        stateStack.addLast(States.INITIAL);
        do {
            /* Parse through the parsable string's tokens. */
            currentState = Jsoner.popNextState(stateStack);
            token = Jsoner.lexNextToken(lexer);
            switch (currentState) {
                case DONE:
                    /* The parse has finished a JSON value. */
                    if (!flags.contains(DeserializationOptions.ALLOW_CONCATENATED_JSON_VALUES)
                            || Yytoken.Types.END.equals(token.getType())) {
                        /*
                         * Break if concatenated values are not allowed or if an END
                         * token is read.
                         */
                        break;
                    }
                    /*
                     * Increment the amount of returned JSON values and treat the
                     * token as if it were a fresh parse.
                     */
                    returnCount += 1;
                    /* Fall through to the case for the initial state. */
                    //$FALL-THROUGH$
                case INITIAL:
                    /* The parse has just started. */
                    switch (token.getType()) {
                        case DATUM:
                            /* A boolean, null, Number, or String could be detected. */
                            if (flags.contains(DeserializationOptions.ALLOW_JSON_DATA)) {
                                valueStack.addLast(token.getValue());
                                stateStack.addLast(States.DONE);
                            } else {
                                throw new DeserializationException(
                                        lexer.getPosition(), DeserializationException.Problems.DISALLOWED_TOKEN, token);
                            }
                            break;
                        case LEFT_BRACE:
                            /* An object is detected. */
                            if (flags.contains(DeserializationOptions.ALLOW_JSON_OBJECTS)) {
                                valueStack.addLast(new JsonObject());
                                stateStack.addLast(States.PARSING_OBJECT);
                            } else {
                                throw new DeserializationException(
                                        lexer.getPosition(), DeserializationException.Problems.DISALLOWED_TOKEN, token);
                            }
                            break;
                        case LEFT_SQUARE:
                            /* An array is detected. */
                            if (flags.contains(DeserializationOptions.ALLOW_JSON_ARRAYS)) {
                                valueStack.addLast(new JsonArray());
                                stateStack.addLast(States.PARSING_ARRAY);
                            } else {
                                throw new DeserializationException(
                                        lexer.getPosition(), DeserializationException.Problems.DISALLOWED_TOKEN, token);
                            }
                            break;
                        default:
                            /* Neither a JSON array or object was detected. */
                            throw new DeserializationException(
                                    lexer.getPosition(), DeserializationException.Problems.UNEXPECTED_TOKEN, token);
                    }
                    break;
                case PARSED_ERROR:
                    /*
                     * The parse could be in this state due to the state stack not
                     * having a state to pop off.
                     */
                    throw new DeserializationException(
                            lexer.getPosition(), DeserializationException.Problems.UNEXPECTED_TOKEN, token);
                case PARSING_ARRAY:
                    switch (token.getType()) {
                        case COMMA:
                            /*
                             * The parse could detect a comma while parsing an array
                             * since it separates each element.
                             */
                            stateStack.addLast(currentState);
                            break;
                        case DATUM:
                            /* The parse found an element of the array. */
                            JsonArray val = (JsonArray) valueStack.getLast();
                            val.add(token.getValue());
                            stateStack.addLast(currentState);
                            break;
                        case LEFT_BRACE:
                            /* The parse found an object in the array. */
                            val = (JsonArray) valueStack.getLast();
                            final JsonObject object = new JsonObject();
                            val.add(object);
                            valueStack.addLast(object);
                            stateStack.addLast(currentState);
                            stateStack.addLast(States.PARSING_OBJECT);
                            break;
                        case LEFT_SQUARE:
                            /* The parse found another array in the array. */
                            val = (JsonArray) valueStack.getLast();
                            final JsonArray array = new JsonArray();
                            val.add(array);
                            valueStack.addLast(array);
                            stateStack.addLast(currentState);
                            stateStack.addLast(States.PARSING_ARRAY);
                            break;
                        case RIGHT_SQUARE:
                            /* The parse found the end of the array. */
                            if (valueStack.size() > returnCount) {
                                valueStack.removeLast();
                            } else {
                                /* The parse has been fully resolved. */
                                stateStack.addLast(States.DONE);
                            }
                            break;
                        default:
                            /* Any other token is invalid in an array. */
                            throw new DeserializationException(
                                    lexer.getPosition(), DeserializationException.Problems.UNEXPECTED_TOKEN, token);
                    }
                    break;
                case PARSING_OBJECT:
                    /* The parse has detected the start of an object. */
                    switch (token.getType()) {
                        case COMMA:
                            /*
                             * The parse could detect a comma while parsing an object
                             * since it separates each key value pair. Continue parsing
                             * the object.
                             */
                            stateStack.addLast(currentState);
                            break;
                        case DATUM:
                            /* The token ought to be a key. */
                            if (token.getValue() instanceof String) {
                                /*
                                 * JSON keys are always strings, strings are not always
                                 * JSON keys but it is going to be treated as one.
                                 * Continue parsing the object.
                                 */
                                final String key = (String) token.getValue();
                                valueStack.addLast(key);
                                stateStack.addLast(currentState);
                                stateStack.addLast(States.PARSING_ENTRY);
                            } else {
                                /*
                                 * Abort! JSON keys are always strings and it wasn't a
                                 * string.
                                 */
                                throw new DeserializationException(
                                        lexer.getPosition(), DeserializationException.Problems.UNEXPECTED_TOKEN, token);
                            }
                            break;
                        case RIGHT_BRACE:
                            /* The parse has found the end of the object. */
                            if (valueStack.size() > returnCount) {
                                /* There are unresolved values remaining. */
                                valueStack.removeLast();
                            } else {
                                /* The parse has been fully resolved. */
                                stateStack.addLast(States.DONE);
                            }
                            break;
                        default:
                            /* The parse didn't detect the end of an object or a key. */
                            throw new DeserializationException(
                                    lexer.getPosition(), DeserializationException.Problems.UNEXPECTED_TOKEN, token);
                    }
                    break;
                case PARSING_ENTRY:
                    switch (token.getType()) {
                        /* Parsed pair keys can only happen while parsing objects. */
                        case COLON:
                            /*
                             * The parse could detect a colon while parsing a key value
                             * pair since it separates the key and value from each
                             * other. Continue parsing the entry.
                             */
                            stateStack.addLast(currentState);
                            break;
                        case DATUM:
                            /* The parse has found a value for the parsed pair key. */
                            String key = (String) valueStack.removeLast();
                            JsonObject parent = (JsonObject) valueStack.getLast();
                            parent.put(key, token.getValue());
                            break;
                        case LEFT_BRACE:
                            /* The parse has found an object for the parsed pair key. */
                            key = (String) valueStack.removeLast();
                            parent = (JsonObject) valueStack.getLast();
                            final JsonObject object = new JsonObject();
                            parent.put(key, object);
                            valueStack.addLast(object);
                            stateStack.addLast(States.PARSING_OBJECT);
                            break;
                        case LEFT_SQUARE:
                            /* The parse has found an array for the parsed pair key. */
                            key = (String) valueStack.removeLast();
                            parent = (JsonObject) valueStack.getLast();
                            final JsonArray array = new JsonArray();
                            parent.put(key, array);
                            valueStack.addLast(array);
                            stateStack.addLast(States.PARSING_ARRAY);
                            break;
                        default:
                            /*
                             * The parse didn't find anything for the parsed pair key.
                             */
                            throw new DeserializationException(
                                    lexer.getPosition(), DeserializationException.Problems.UNEXPECTED_TOKEN, token);
                    }
                    break;
                default:
                    break;
            }
            /* If we're not at the END and DONE then do the above again. */
        } while (!(States.DONE.equals(currentState) && Yytoken.Types.END.equals(token.getType())));
        return new JsonArray(valueStack);
    }

    /**
     * A convenience method that assumes a StringReader to deserialize a string.
     *
     * @param  deserializable           representing content to be deserialized as JSON.
     * @return                          either a boolean, null, Number, String, JsonObject, or JsonArray that best
     *                                  represents the deserializable.
     * @throws DeserializationException if an unexpected token is encountered in the deserializable. To recover from a
     *                                  DeserializationException: fix the deserializable to no longer have an unexpected
     *                                  token and try again.
     * @see                             Jsoner#deserialize(Reader)
     * @see                             StringReader
     */
    public static Object deserialize(final String deserializable) throws DeserializationException {
        try (StringReader readableDeserializable = new StringReader(deserializable)) {

            return Jsoner.deserialize(readableDeserializable);
        } catch (IOException | NullPointerException caught) {
            /*
             * They both have the same recovery scenario. See StringReader. If
             * deserializable is null, it should be reasonable to expect null
             * back.
             */
            return null;
        }
    }

    /**
     * A convenience method that assumes a JsonArray must be deserialized.
     *
     * @param  deserializable representing content to be deserializable as a JsonArray.
     * @param  defaultValue   representing what would be returned if deserializable isn't a JsonArray or an IOException,
     *                        NullPointerException, or DeserializationException occurs during deserialization.
     * @return                a JsonArray that represents the deserializable, or the defaultValue if there isn't a
     *                        JsonArray that represents deserializable.
     * @see                   Jsoner#deserialize(Reader)
     */
    public static JsonArray deserialize(final String deserializable, final JsonArray defaultValue) {
        try (StringReader readable = new StringReader(deserializable)) {
            return Jsoner.deserialize(readable, EnumSet.of(DeserializationOptions.ALLOW_JSON_ARRAYS)).<
                    JsonArray> getCollection(0);
        } catch (NullPointerException | IOException | DeserializationException caught) {
            /* Don't care, just return the default value. */
            return defaultValue;
        }
    }

    /**
     * A convenience method that assumes a JsonObject must be deserialized.
     *
     * @param  deserializable representing content to be deserializable as a JsonObject.
     * @param  defaultValue   representing what would be returned if deserializable isn't a JsonObject or an
     *                        IOException, NullPointerException, or DeserializationException occurs during
     *                        deserialization.
     * @return                a JsonObject that represents the deserializable, or the defaultValue if there isn't a
     *                        JsonObject that represents deserializable.
     * @see                   Jsoner#deserialize(Reader)
     */
    public static JsonObject deserialize(final String deserializable, final JsonObject defaultValue) {
        try (StringReader readable = new StringReader(deserializable)) {
            return Jsoner.deserialize(readable, EnumSet.of(DeserializationOptions.ALLOW_JSON_OBJECTS)).<
                    JsonObject> getMap(0);
        } catch (NullPointerException | IOException | DeserializationException caught) {
            /* Don't care, just return the default value. */
            return defaultValue;
        }
    }

    /**
     * A convenience method that assumes multiple RFC 4627 JSON values (except numbers) have been concatenated together
     * for deserilization which will be collectively returned in a JsonArray wrapper. There may be numbers included,
     * they just must not be concatenated together as it is prone to NumberFormatExceptions (thus causing a
     * DeserializationException) or the numbers no longer represent their respective values. Examples: "123null321"
     * returns [123, null, 321] "nullnullnulltruefalse\"\"{}[]" returns [null, null, null, true, false, "", {}, []]
     * "123" appended to "321" returns [123321] "12.3" appended to "3.21" throws
     * DeserializationException(NumberFormatException) "123" appended to "-321" throws
     * DeserializationException(NumberFormatException) "123e321" appended to "-1" throws
     * DeserializationException(NumberFormatException) "null12.33.21null" throws
     * DeserializationException(NumberFormatException)
     *
     * @param  deserializable           representing concatenated content to be deserialized as JSON in one reader. Its
     *                                  contents may not contain two numbers concatenated together.
     * @return                          a JsonArray that contains each of the concatenated objects as its elements. Each
     *                                  concatenated element is either a boolean, null, Number, String, JsonArray, or
     *                                  JsonObject that best represents the concatenated content inside deserializable.
     * @throws DeserializationException if an unexpected token is encountered in the deserializable. To recover from a
     *                                  DeserializationException: fix the deserializable to no longer have an unexpected
     *                                  token and try again.
     * @throws IOException              when the underlying reader encounters an I/O error. Ensure the reader is
     *                                  properly instantiated, isn't closed, or that it is ready before trying again.
     */
    public static JsonArray deserializeMany(final Reader deserializable) throws DeserializationException, IOException {
        return Jsoner.deserialize(deserializable,
                EnumSet.of(DeserializationOptions.ALLOW_JSON_ARRAYS, DeserializationOptions.ALLOW_JSON_OBJECTS,
                        DeserializationOptions.ALLOW_JSON_DATA, DeserializationOptions.ALLOW_CONCATENATED_JSON_VALUES));
    }

    /**
     * Escapes potentially confusing or important characters in the String provided.
     *
     * @param  escapable an unescaped string.
     * @return           an escaped string for usage in JSON; An escaped string is one that has escaped all of the
     *                   quotes ("), backslashes (\), return character (\r), new line character (\n), tab character
     *                   (\t), backspace character (\b), form feed character (\f) and other control characters
     *                   [u0000..u001F] or characters [u007F..u009F], [u2000..u20FF] with a backslash (\) which itself
     *                   must be escaped by the backslash in a java string.
     */
    public static String escape(final String escapable) {
        final StringBuilder builder = new StringBuilder();
        final int characters = escapable.length();
        for (int i = 0; i < characters; i++) {
            final char character = escapable.charAt(i);
            switch (character) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                case '/':
                    builder.append("\\/");
                    break;
                default:
                    /*
                     * The many characters that get replaced are benign to software
                     * but could be mistaken by people reading it for a JSON
                     * relevant character.
                     */
                    if (character >= '\u0000' && character <= '\u001F'
                            || character >= '\u007F' && character <= '\u009F'
                            || character >= '\u2000' && character <= '\u20FF') {
                        final String characterHexCode = Integer.toHexString(character);
                        builder.append("\\u");
                        builder.append("0".repeat(4 - characterHexCode.length()));
                        builder.append(characterHexCode.toUpperCase());
                    } else {
                        /* Character didn't need escaping. */
                        builder.append(character);
                    }
            }
        }
        return builder.toString();
    }

    /**
     * Un-escapes a JSon string provided.
     *
     * @param  json a escaped string.
     * @return      an unescaped JSon string (plain string)
     */
    public static String unescape(String json) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            char delimiter = json.charAt(i);
            i++;
            if (delimiter == '\\' && i < json.length()) {
                char ch = json.charAt(i);
                i++;
                if (ch == '\\' || ch == '/' || ch == '"' || ch == '\'') {
                    sb.append(ch);
                } else if (ch == 'n') {
                    sb.append('\n');
                } else if (ch == 'r') {
                    sb.append('\r');
                } else if (ch == 't') {
                    sb.append('\t');
                } else if (ch == 'b') {
                    sb.append('\b');
                } else if (ch == 'f') {
                    sb.append('\f');
                } else if (ch == 'u') {
                    StringBuilder hex = new StringBuilder();
                    // expect 4 digits
                    if (i + 4 > json.length()) {
                        throw new RuntimeException("Not enough unicode digits! ");
                    }
                    for (char x : json.substring(i, i + 4).toCharArray()) {
                        if (!Character.isLetterOrDigit(x)) {
                            throw new RuntimeException("Bad character in unicode escape.");
                        }
                        hex.append(Character.toLowerCase(x));
                    }
                    i += 4; // consume those four digits.
                    int code = Integer.parseInt(hex.toString(), 16);
                    sb.append((char) code);
                } else {
                    throw new RuntimeException("Illegal escape sequence: \\" + ch);
                }
            } else {
                // it's not a backslash, or it's the last character.
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    /**
     * Processes the lexer's reader for the next token.
     *
     * @param  lexer                    represents a text processor being used in the deserialization process.
     * @return                          a token representing a meaningful element encountered by the lexer.
     * @throws DeserializationException if an unexpected character is encountered while processing the text.
     * @throws IOException              if the underlying reader inside the lexer encounters an I/O problem, like being
     *                                  prematurely closed.
     */
    private static Yytoken lexNextToken(final Yylex lexer) throws DeserializationException, IOException {
        Yytoken returnable;
        /* Parse through the next token. */
        returnable = lexer.yylex();
        if (returnable == null) {
            /* If there isn't another token, it must be the end. */
            returnable = new Yytoken(Yytoken.Types.END, null);
        }
        return returnable;
    }

    /**
     * Used for state transitions while deserializing.
     *
     * @param  stateStack represents the deserialization states saved for future processing.
     * @return            a state for deserialization context so it knows how to consume the next token.
     */
    private static States popNextState(final LinkedList<States> stateStack) {
        if (!stateStack.isEmpty()) {
            return stateStack.removeLast();
        } else {
            return States.PARSED_ERROR;
        }
    }

    /**
     * Formats the JSON string to be more easily human readable using tabs for indentation.
     *
     * @param  printable representing a JSON formatted string with out extraneous characters, like one returned from
     *                   Jsoner#serialize(Object).
     * @return           printable except it will have '\n' then '\t' characters inserted after '[', '{', ',' and before
     *                   ']' '}' tokens in the JSON. It will return null if printable isn't a JSON string.
     */
    public static String prettyPrint(final String printable) {
        return Jsoner.prettyPrint(printable, "\t");
    }

    /**
     * Formats the JSON string to be more easily human readable using an arbitrary amount of spaces for indentation.
     *
     * @param  printable                representing a JSON formatted string with out extraneous characters, like one
     *                                  returned from Jsoner#serialize(Object).
     * @param  spaces                   representing the amount of spaces to use for indentation. Must be between 2 and
     *                                  10.
     * @return                          printable except it will have '\n' then space characters inserted after '[',
     *                                  '{', ',' and before ']' '}' tokens in the JSON. It will return null if printable
     *                                  isn't a JSON string.
     * @throws IllegalArgumentException if spaces isn't between [2..10].
     * @see                             Jsoner#prettyPrint(String)
     * @since                           2.2.0 to allow pretty printing with spaces instead of tabs.
     */
    public static String prettyPrint(final String printable, final int spaces) {
        return prettyPrint(printable, spaces, Integer.MAX_VALUE);
    }

    public static String prettyPrint(final String printable, final int spaces, final int depth) {
        if (spaces > 10 || spaces < 2) {
            throw new IllegalArgumentException("Indentation with spaces must be between 2 and 10.");
        }

        return Jsoner.prettyPrint(printable, " ".repeat(spaces), depth);
    }

    /**
     * Makes the JSON string more easily human readable using indentation of the caller's choice.
     *
     * @param  printable   representing a JSON formatted string with out extraneous characters, like one returned from
     *                     Jsoner#serialize(Object).
     * @param  indentation representing the indentation used to format the JSON string.
     * @return             printable except it will have '\n' then indentation characters inserted after '[', '{', ','
     *                     and before ']' '}' tokens in the JSON. It will return null if printable isn't a JSON string.
     */
    private static String prettyPrint(final String printable, final String indentation) {
        return prettyPrint(printable, indentation, Integer.MAX_VALUE);
    }

    private static String prettyPrint(final String printable, final String indentation, final int depth) {
        final Yylex lexer = new Yylex(new StringReader(printable));
        Yytoken lexed;
        final StringBuilder returnable = new StringBuilder();
        int level = 0;
        try {
            do {
                lexed = Jsoner.lexNextToken(lexer);
                switch (lexed.getType()) {
                    case COLON:
                        returnable.append(": ");
                        break;
                    case COMMA:
                        returnable.append(lexed.getValue());
                        if (level <= depth) {
                            returnable.append("\n");
                            returnable.append(String.valueOf(indentation).repeat(level));
                        } else {
                            returnable.append(" ");
                        }
                        break;
                    case END:
                        returnable.append("\n");
                        break;
                    case LEFT_BRACE:
                    case LEFT_SQUARE:
                        returnable.append(lexed.getValue());
                        if (++level <= depth) {
                            returnable.append("\n");
                            returnable.append(String.valueOf(indentation).repeat(level));
                        } else {
                            returnable.append(" ");
                        }
                        break;
                    case RIGHT_BRACE:
                    case RIGHT_SQUARE:
                        if (level-- <= depth) {
                            returnable.append("\n");
                            returnable.append(String.valueOf(indentation).repeat(level));
                        } else {
                            returnable.append(" ");
                        }
                        returnable.append(lexed.getValue());
                        break;
                    default:
                        if (lexed.getValue() instanceof String) {
                            returnable.append("\"");
                            returnable.append(Jsoner.escape((String) lexed.getValue()));
                            returnable.append("\"");
                        } else {
                            returnable.append(lexed.getValue());
                        }
                        break;
                }
            } while (!lexed.getType().equals(Yytoken.Types.END));
        } catch (final DeserializationException caught) {
            /* This is according to the method's contract. */
            return null;
        } catch (final IOException caught) {
            /* See StringReader. */
            return null;
        }
        return returnable.toString();
    }

    @FunctionalInterface
    public interface ColorPrintElement {
        String color(Yytoken.Types type, Object value);
    }

    public static String colorPrint(final String printable, final ColorPrintElement color) {
        return Jsoner.colorPrint(printable, "\t", Integer.MAX_VALUE, true, color);
    }

    public static String colorPrint(final String printable, final int spaces, final ColorPrintElement color) {
        return colorPrint(printable, spaces, true, color);
    }

    public static String colorPrint(
            final String printable, final int spaces, final boolean pretty, final ColorPrintElement color) {
        if (spaces > 10 || spaces < 2) {
            throw new IllegalArgumentException("Indentation with spaces must be between 2 and 10.");
        }

        return Jsoner.colorPrint(printable, " ".repeat(spaces), Integer.MAX_VALUE, pretty, color);
    }

    public static String colorPrint(
            final String printable, final String indentation, final int depth, final boolean pretty, ColorPrintElement color) {
        final Yylex lexer = new Yylex(new StringReader(printable));
        Yytoken lexed;
        final StringBuilder returnable = new StringBuilder();
        int level = 0;
        try {
            do {
                lexed = Jsoner.lexNextToken(lexer);
                switch (lexed.getType()) {
                    case COLON:
                        returnable.append(color.color(Yytoken.Types.COLON, ":"));
                        if (pretty) {
                            returnable.append(" ");
                        }
                        break;
                    case COMMA:
                        returnable.append(color.color(Yytoken.Types.COMMA, lexed.getValue()));
                        if (level <= depth) {
                            if (pretty) {
                                returnable.append("\n");
                                returnable.append(String.valueOf(indentation).repeat(level));
                            }
                        } else {
                            if (pretty) {
                                returnable.append(" ");
                            }
                        }
                        break;
                    case END:
                        if (pretty) {
                            returnable.append("\n");
                        }
                        break;
                    case LEFT_BRACE:
                        returnable.append(color.color(Yytoken.Types.LEFT_BRACE, lexed.getValue()));
                        if (++level <= depth) {
                            if (pretty) {
                                returnable.append("\n");
                                returnable.append(String.valueOf(indentation).repeat(level));
                            }
                        } else {
                            if (pretty) {
                                returnable.append(" ");
                            }
                        }
                        break;
                    case LEFT_SQUARE:
                        returnable.append(color.color(Yytoken.Types.LEFT_SQUARE, lexed.getValue()));
                        if (++level <= depth) {
                            if (pretty) {
                                returnable.append("\n");
                                returnable.append(String.valueOf(indentation).repeat(level));
                            }
                        } else {
                            if (pretty) {
                                returnable.append(" ");
                            }
                        }
                        break;
                    case RIGHT_BRACE:
                        if (level-- <= depth) {
                            if (pretty) {
                                returnable.append("\n");
                                returnable.append(String.valueOf(indentation).repeat(level));
                            }
                        } else {
                            if (pretty) {
                                returnable.append(" ");
                            }
                        }
                        returnable.append(color.color(Yytoken.Types.RIGHT_BRACE, lexed.getValue()));
                        break;
                    case RIGHT_SQUARE:
                        if (level-- <= depth) {
                            if (pretty) {
                                returnable.append("\n");
                                returnable.append(String.valueOf(indentation).repeat(level));
                            }
                        } else {
                            if (pretty) {
                                returnable.append(" ");
                            }
                        }
                        returnable.append(color.color(Yytoken.Types.RIGHT_SQUARE, lexed.getValue()));
                        break;
                    default:
                        if (lexed.getValue() instanceof String) {
                            String s = "\"" + Jsoner.escape((String) lexed.getValue()) + "\"";
                            returnable.append(color.color(Yytoken.Types.VALUE, s));
                        } else {
                            returnable.append(color.color(Yytoken.Types.VALUE, lexed.getValue()));
                        }
                        break;
                }
            } while (!lexed.getType().equals(Yytoken.Types.END));
        } catch (final DeserializationException caught) {
            /* This is according to the method's contract. */
            return null;
        } catch (final IOException caught) {
            /* See StringReader. */
            return null;
        }
        return returnable.toString();
    }

    /**
     * A convenience method that assumes a StringWriter.
     *
     * @param  jsonSerializable         represents the object that should be serialized as a string in JSON format.
     * @return                          a string, in JSON format, that represents the object provided.
     * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
     * @see                             Jsoner#serialize(Object, Writer)
     * @see                             StringWriter
     */
    public static String serialize(final Object jsonSerializable) {
        final StringWriter writableDestination = new StringWriter();
        try {
            Jsoner.serialize(jsonSerializable, writableDestination);
        } catch (final IOException caught) {
            /* See StringWriter. */
        }
        return writableDestination.toString();
    }

    /**
     * A convenience method that assumes a StringWriter.
     *
     * @param  jsonSerializable         represents the object that should be serialized as a string in JSON format.
     * @return                          a string, in JSON format, that represents the object provided, or <tt>null</tt>
     *                                  if not possible to serialize.
     * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
     * @see                             Jsoner#serialize(Object, Writer)
     * @see                             StringWriter
     */
    public static String trySerialize(final Object jsonSerializable) {
        final StringWriter writableDestination = new StringWriter();
        try {
            Jsoner.serialize(jsonSerializable, writableDestination,
                    EnumSet.of(SerializationOptions.ALLOW_JSONABLES, SerializationOptions.ALLOW_FULLY_QUALIFIED_ENUMERATIONS,
                            SerializationOptions.ALLOW_INVALIDS_NOOP));
        } catch (final IOException caught) {
            /* See StringWriter. */
        }
        String answer = writableDestination.toString();
        if (answer != null && answer.contains("SerializationOptions.ALLOW_INVALIDS_NOOP")) {
            answer = null;
        }
        return answer;
    }

    /**
     * Serializes values according to the RFC 4627 JSON specification. It will also trust the serialization provided by
     * any Jsonables it serializes and serializes Enums that don't implement Jsonable as a string of their fully
     * qualified name.
     *
     * @param  jsonSerializable         represents the object that should be serialized in JSON format.
     * @param  writableDestination      represents where the resulting JSON text is written to.
     * @throws IOException              if the writableDestination encounters an I/O problem, like being closed while in
     *                                  use.
     * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
     */
    public static void serialize(final Object jsonSerializable, final Writer writableDestination) throws IOException {
        Jsoner.serialize(jsonSerializable, writableDestination,
                EnumSet.of(SerializationOptions.ALLOW_JSONABLES, SerializationOptions.ALLOW_FULLY_QUALIFIED_ENUMERATIONS));
    }

    /**
     * Serialize values to JSON and write them to the provided writer based on behavior flags.
     *
     * @param  jsonSerializable         represents the object that should be serialized to a string in JSON format.
     * @param  writableDestination      represents where the resulting JSON text is written to.
     * @param  flags                    represents the allowances and restrictions on serialization.
     * @throws IOException              if the writableDestination encounters an I/O problem.
     * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
     * @see                             SerializationOptions
     */
    private static void serialize(
            final Object jsonSerializable, final Writer writableDestination, final Set<SerializationOptions> flags)
            throws IOException {
        if (jsonSerializable == null) {
            /* When a null is passed in the word null is supported in JSON. */
            writableDestination.write("null");
        } else if (jsonSerializable instanceof Jsonable && flags.contains(SerializationOptions.ALLOW_JSONABLES)) {
            /* Writes the writable as defined by the writable. */
            writableDestination.write(((Jsonable) jsonSerializable).toJson());
        } else if (jsonSerializable instanceof Enum
                && flags.contains(SerializationOptions.ALLOW_FULLY_QUALIFIED_ENUMERATIONS)) {
            /*
             * Writes the enum as a special case of string. All enums (unless
             * they implement Jsonable) will be the string literal
             * "${DECLARING_CLASS_NAME}.${ENUM_NAME}" as their value.
             */
            @SuppressWarnings("rawtypes")
            final Enum e = (Enum) jsonSerializable;
            writableDestination.write('"');
            writableDestination.write(e.getDeclaringClass().getName());
            writableDestination.write('.');
            writableDestination.write(e.name());
            writableDestination.write('"');
        } else if (jsonSerializable instanceof String) {
            /* Make sure the string is properly escaped. */
            writableDestination.write('"');
            writableDestination.write(Jsoner.escape((String) jsonSerializable));
            writableDestination.write('"');
        } else if (jsonSerializable instanceof Double) {
            if (((Double) jsonSerializable).isInfinite() || ((Double) jsonSerializable).isNaN()) {
                /*
                 * Infinite and not a number are not supported by the JSON
                 * specification, so null is used instead.
                 */
                writableDestination.write("null");
            } else {
                writableDestination.write(jsonSerializable.toString());
            }
        } else if (jsonSerializable instanceof Float) {
            if (((Float) jsonSerializable).isInfinite() || ((Float) jsonSerializable).isNaN()) {
                /*
                 * Infinite and not a number are not supported by the JSON
                 * specification, so null is used instead.
                 */
                writableDestination.write("null");
            } else {
                writableDestination.write(jsonSerializable.toString());
            }
        } else if (jsonSerializable instanceof Number) {
            writableDestination.write(jsonSerializable.toString());
        } else if (jsonSerializable instanceof Boolean) {
            writableDestination.write(jsonSerializable.toString());
        } else if (jsonSerializable instanceof Map) {
            /* Writes the map in JSON object format. */
            boolean isFirstEntry = true;
            @SuppressWarnings("rawtypes")
            final Iterator entries = ((Map) jsonSerializable).entrySet().iterator();
            writableDestination.write('{');
            while (entries.hasNext()) {
                if (isFirstEntry) {
                    isFirstEntry = false;
                } else {
                    writableDestination.write(',');
                }
                @SuppressWarnings("rawtypes")
                final Map.Entry entry = (Map.Entry) entries.next();
                Jsoner.serialize(entry.getKey(), writableDestination, flags);
                writableDestination.write(':');
                Jsoner.serialize(entry.getValue(), writableDestination, flags);
            }
            writableDestination.write('}');
        } else if (jsonSerializable instanceof Collection) {
            /* Writes the collection in JSON array format. */
            boolean isFirstElement = true;
            @SuppressWarnings("rawtypes")
            final Iterator elements = ((Collection) jsonSerializable).iterator();
            writableDestination.write('[');
            while (elements.hasNext()) {
                if (isFirstElement) {
                    isFirstElement = false;
                } else {
                    writableDestination.write(',');
                }
                Jsoner.serialize(elements.next(), writableDestination, flags);
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof byte[]) {
            /* Writes the array in JSON array format. */
            final byte[] writableArray = (byte[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof short[]) {
            /* Writes the array in JSON array format. */
            final short[] writableArray = (short[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof int[]) {
            /* Writes the array in JSON array format. */
            final int[] writableArray = (int[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof long[]) {
            /* Writes the array in JSON array format. */
            final long[] writableArray = (long[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof float[]) {
            /* Writes the array in JSON array format. */
            final float[] writableArray = (float[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof double[]) {
            /* Writes the array in JSON array format. */
            final double[] writableArray = (double[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof boolean[]) {
            /* Writes the array in JSON array format. */
            final boolean[] writableArray = (boolean[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(',');
                }
            }
            writableDestination.write(']');
        } else if (jsonSerializable instanceof char[]) {
            /* Writes the array in JSON array format. */
            final char[] writableArray = (char[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write("[\"");
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write("\",\"");
                }
            }
            writableDestination.write("\"]");
        } else if (jsonSerializable instanceof Object[]) {
            /* Writes the array in JSON array format. */
            final Object[] writableArray = (Object[]) jsonSerializable;
            final int numberOfElements = writableArray.length;
            writableDestination.write('[');
            for (int i = 0; i < numberOfElements; i++) {
                if (i == (numberOfElements - 1)) {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                } else {
                    Jsoner.serialize(writableArray[i], writableDestination, flags);
                    writableDestination.write(",");
                }
            }
            writableDestination.write(']');
        } else {
            /*
             * TODO a potential feature for future release since POJOs are often
             * represented as JsonObjects. It would be nice to have a flag that
             * tries to reflectively figure out what a non-Jsonable POJO's
             * fields are and use their names as keys and their respective
             * values for the keys' values in the JsonObject? Naturally
             * implementing Jsonable is safer and in many ways makes this
             * feature a convenience for not needing to implement Jsonable for
             * very simple POJOs. If it fails to produce a JsonObject to
             * serialize it should defer to replacements if allowed. If
             * replacement fails it should defer to invalids if allowed. This
             * feature would require another serialize method exposed to allow
             * this serialization. This feature (although perhaps useful on its
             * own) would also include a method in the JsonObject where you pass
             * it a class and it would do its best to instantiate a POJO of the
             * class using the keys in the JsonObject.
             */
            /*
             * It cannot by any measure be safely serialized according to
             * specification.
             */
            if (flags.contains(SerializationOptions.ALLOW_INVALIDS_NOOP)) {
                // noop marker
                writableDestination.write("SerializationOptions.ALLOW_INVALIDS_NOOP");
            } else if (flags.contains(SerializationOptions.ALLOW_INVALIDS)) {
                /* Can be helpful for debugging how it isn't valid. */
                writableDestination.write(jsonSerializable.toString());
            } else {
                /*
                 * Notify the caller the cause of failure for the serialization.
                 */
                throw new IllegalArgumentException(
                        "Encountered a: " + jsonSerializable.getClass().getName() + " as: " + jsonSerializable.toString()
                                                   + "  that isn't JSON serializable.\n  Try:\n"
                                                   + "    1) Implementing the Jsonable interface for the object to return valid JSON. If it already does it probably has a bug.\n"
                                                   + "    2) If you cannot edit the source of the object or couple it with this library consider wrapping it in a class that does implement the Jsonable interface.\n"
                                                   + "    3) Otherwise convert it to a boolean, null, number, JsonArray, JsonObject, or String value before serializing it.\n"
                                                   + "    4) If you feel it should have serialized you could use a more tolerant serialization for debugging purposes.");
            }
        }
    }

    /**
     * Serializes like the first version of this library. It has been adapted to use Jsonable for serializing custom
     * objects, but otherwise works like the old JSON string serializer. It will allow non-JSON values in its output
     * like the old one. It can be helpful for last resort log statements and debugging errors in self generated JSON.
     * Anything serialized using this method isn't guaranteed to be deserializable.
     *
     * @param  jsonSerializable    represents the object that should be serialized in JSON format.
     * @param  writableDestination represents where the resulting JSON text is written to.
     * @throws IOException         if the writableDestination encounters an I/O problem, like being closed while in use.
     */
    public static void serializeCarelessly(final Object jsonSerializable, final Writer writableDestination) throws IOException {
        Jsoner.serialize(jsonSerializable, writableDestination,
                EnumSet.of(SerializationOptions.ALLOW_JSONABLES, SerializationOptions.ALLOW_INVALIDS));
    }

    /**
     * Serializes JSON values and only JSON values according to the RFC 4627 JSON specification.
     *
     * @param  jsonSerializable         represents the object that should be serialized in JSON format.
     * @param  writableDestination      represents where the resulting JSON text is written to.
     * @throws IOException              if the writableDestination encounters an I/O problem, like being closed while in
     *                                  use.
     * @throws IllegalArgumentException if the jsonSerializable isn't serializable in JSON.
     */
    public static void serializeStrictly(final Object jsonSerializable, final Writer writableDestination) throws IOException {
        Jsoner.serialize(jsonSerializable, writableDestination, EnumSet.noneOf(SerializationOptions.class));
    }
}
