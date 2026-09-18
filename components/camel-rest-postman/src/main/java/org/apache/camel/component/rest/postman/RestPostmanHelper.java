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
package org.apache.camel.component.rest.postman;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.StringHelper;

/**
 * Static helpers shared by the component, endpoint and consumer.
 */
public final class RestPostmanHelper {

    private static final Pattern HOST_PATTERN = Pattern.compile("https?://[^:/]+(:\\d+)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?:[0-9]+-)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * An HTTP method, constrained so that a hostile or malformed collection cannot inject extra segments into the colon
     * delimited {@code rest:} URI that is built from it.
     */
    private static final Pattern METHOD_PATTERN = Pattern.compile("[A-Za-z]{1,20}");

    /**
     * Characters that would break out of a path segment and corrupt the delegated endpoint URI.
     */
    private static final Pattern UNSAFE_SEGMENT = Pattern.compile("[?#&:]");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private RestPostmanHelper() {
    }

    public static String isMediaRange(final String given, final String name) {
        return StringHelper.notEmpty(given, name);
    }

    /**
     * Validates that a host option is an absolute URI naming only a scheme, host and optional port.
     */
    public static String isHostParam(final String given) {
        final String hostUri = StringHelper.notEmpty(given, "host");

        final Matcher matcher = HOST_PATTERN.matcher(given);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "host must be an absolute URI (e.g. http://api.example.com), given: `" + hostUri + "`");
        }
        return hostUri;
    }

    /**
     * Whether the text is a Postman UUID, or the {@code {ownerId}-{uuid}} uid form.
     */
    public static boolean isUuid(String text) {
        return text != null && UUID_PATTERN.matcher(text).matches();
    }

    /**
     * Validates an HTTP method read from a collection.
     */
    public static String validateMethod(String method, String itemDescription) {
        if (method == null || !METHOD_PATTERN.matcher(method).matches()) {
            throw new IllegalArgumentException(
                    "Postman request " + itemDescription + " declares an invalid HTTP method: " + method);
        }
        return method.toUpperCase(Locale.ROOT);
    }

    /**
     * Validates a path segment after variable substitution.
     * <p>
     * Substituted values can originate outside the collection, by way of the {@code variables} option and Camel
     * property placeholders, so they are checked before being concatenated into the delegated endpoint URI.
     */
    public static String validatePathSegment(String segment, String itemDescription) {
        if (UNSAFE_SEGMENT.matcher(segment).find()) {
            throw new IllegalArgumentException(
                    "Postman request " + itemDescription + " has a URL path segment containing one of ? # & :"
                                               + " after variable substitution, which cannot be expressed as a REST"
                                               + " endpoint: `" + segment + "`");
        }
        return segment;
    }

    /**
     * Converts a Postman item name into a camel case identifier usable in an endpoint URI, for example
     * {@code "Get User By Id"} becomes {@code getUserById}.
     *
     * @param  name     the item name, may be {@code null} or empty
     * @param  fallback used when the name yields no usable characters at all
     * @return          the slug
     */
    public static String slugify(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        // fold accented characters down to ASCII so that "Créer Utilisateur" yields "creerUtilisateur"
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");

        String[] tokens = NON_ALPHANUMERIC.split(normalized);
        StringBuilder answer = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (answer.isEmpty()) {
                answer.append(token.toLowerCase(Locale.ROOT));
            } else {
                answer.append(Character.toUpperCase(token.charAt(0)));
                if (token.length() > 1) {
                    answer.append(token.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        if (answer.isEmpty()) {
            return fallback;
        }
        // an identifier must not start with a digit, so that it stays usable as a bean or route id
        if (Character.isDigit(answer.charAt(0))) {
            answer.insert(0, 'r');
        }
        return answer.toString();
    }

    /**
     * Builds the placeholder expression that {@code camel-rest} resolves per exchange, from a message header falling
     * back to an exchange variable.
     *
     * @param name     the parameter name
     * @param required when {@code false} the parameter is dropped if it cannot be resolved
     */
    public static String queryParameterExpression(String name, boolean required) {
        return name + "={" + name + (required ? "" : "?") + "}";
    }

    /**
     * Derives a host from the REST configuration, skipping the default port for the scheme.
     *
     * @return the host, or {@code null} when the configuration does not define one
     */
    public static String hostFrom(final RestConfiguration restConfiguration) {
        if (restConfiguration == null) {
            return null;
        }

        final String scheme = restConfiguration.getScheme();
        final String host = restConfiguration.getHost();
        final int port = restConfiguration.getPort();

        if (scheme == null || host == null) {
            return null;
        }

        final StringBuilder answer = new StringBuilder(scheme).append("://").append(host);
        if (port > 0 && !("http".equalsIgnoreCase(scheme) && port == 80)
                && !("https".equalsIgnoreCase(scheme) && port == 443)) {
            answer.append(':').append(port);
        }
        return answer.toString();
    }
}
