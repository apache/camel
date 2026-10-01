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
package org.apache.camel.component.openfga;

/**
 * Checks that a resolved subject or object can be used.
 * <p/>
 * OpenFGA validates these identifiers too and rejects a malformed one with an HTTP 400, so this is not about saving the
 * server from bad input. It is about <em>where the rejection lands</em> on the authorization path: an HTTP 400 is a
 * failure to obtain a verdict, and with {@code failOpen} set a failure becomes an allow. An exchange whose identity did
 * not resolve must be denied, not allowed, so the check happens here - before the call - and produces a decision rather
 * than a failure.
 */
final class OpenFgaIdentifiers {

    /**
     * OpenFGA's own limit on a user or object identifier, from the {@code ^[^\s]{2,512}$} pattern its API enforces.
     */
    static final int MAX_LENGTH = 512;

    private static final char TYPE_SEPARATOR = ':';
    private static final char USERSET_SEPARATOR = '#';
    private static final String WILDCARD = "*";

    private OpenFgaIdentifiers() {
    }

    /**
     * Whether the identifier names a type but no id - {@code user:} with nothing after the colon.
     * <p/>
     * This is what a configured prefix plus an unresolved expression produces: {@code user:${exchangeProperty.subject}}
     * on an exchange that never carried a subject leaves exactly {@code user:}. It is malformed, but reporting it as
     * malformed buries the thing that actually went wrong, so the caller treats it as a missing identity instead.
     */
    static boolean hasBlankId(String value) {
        int separator = value.indexOf(TYPE_SEPARATOR);
        return separator > 0 && separator == value.length() - 1;
    }

    /**
     * Validates an identifier for use as the subject or the object of a check.
     *
     * @param  value the resolved identifier, which the caller has already established is not blank
     * @return       null when the identifier is usable, otherwise the value for the
     *               {@link OpenFgaConstants#DENY_REASON} header
     */
    static String validate(String value) {
        String reason = validateTupleValue(value);
        if (reason != null) {
            return reason;
        }
        if (isTypedWildcard(value)) {
            // user:* means "everyone", and writing a tuple for it is a legitimate way to share something publicly.
            // Asking "may everyone read this" is not, however, asking whether the caller may. Verified against
            // OpenFGA 1.21.0: check(user:*, reader, document:public) answers true wherever such a tuple exists, so an
            // expression that resolved to a wildcard would hand out every publicly shared object. Deny instead.
            return "wildcard-subject";
        }
        return null;
    }

    /**
     * Validates an identifier for use in a relationship tuple, where a typed wildcard is a legitimate value - that is
     * how a resource is shared with everyone.
     *
     * @param  value the identifier, which may be null or blank
     * @return       null when the identifier is usable, otherwise a short reason for the rejection
     */
    static String validateTupleValue(String value) {
        if (value == null || value.isEmpty()) {
            return "invalid-identifier";
        }
        if (value.length() > MAX_LENGTH) {
            return "invalid-identifier";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // OpenFGA forbids whitespace outright, and a newline in particular would let a crafted identity forge
            // extra lines in anything that logs the tuple
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return "invalid-identifier";
            }
        }
        int separator = value.indexOf(TYPE_SEPARATOR);
        // an identifier is type:id; anything without both halves is not one, and on the check path sending it would
        // only earn an HTTP 400 that failOpen could then read as an allow
        if (separator <= 0 || separator == value.length() - 1) {
            return "invalid-identifier";
        }
        return null;
    }

    /**
     * Validates a relation name, which is a bare name rather than a {@code type:id} identifier.
     *
     * @return null when the name is usable, otherwise a short reason for the rejection
     */
    static String validateRelation(String value) {
        if (value == null || value.isEmpty()) {
            return "missing-relation";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)
                    || c == TYPE_SEPARATOR || c == USERSET_SEPARATOR || c == '@') {
                return "invalid-relation";
            }
        }
        return null;
    }

    /**
     * Whether the identifier's id part is the {@code *} wildcard. A userset - {@code team:eng#member} - is left alone:
     * its id is concrete and only the relation follows.
     */
    private static boolean isTypedWildcard(String value) {
        int separator = value.indexOf(TYPE_SEPARATOR);
        int end = value.indexOf(USERSET_SEPARATOR, separator);
        String id = end < 0 ? value.substring(separator + 1) : value.substring(separator + 1, end);
        return WILDCARD.equals(id);
    }
}
