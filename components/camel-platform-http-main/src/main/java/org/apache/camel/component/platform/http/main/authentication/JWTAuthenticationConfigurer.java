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
package org.apache.camel.component.platform.http.main.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.List;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationConfigEntry;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationHandlerFactory;
import org.apache.camel.main.HttpManagementServerConfigurationProperties;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.util.ObjectHelper;

public class JWTAuthenticationConfigurer implements MainAuthenticationConfigurer {

    // the aliases Vert.x looks up in a JWT keystore, one per algorithm
    // (io.vertx.ext.auth.impl.jose.JWK#load in vertx-auth-common 5.2.3: check it when upgrading Vert.x)
    static final List<String> JWT_KEYSTORE_ALIASES = List.of(
            "HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "ES256K", "ES256", "ES384", "ES512");

    @Override
    public void configureAuthentication(
            AuthenticationConfig authenticationConfig,
            HttpServerConfigurationProperties properties) {

        String path = resolveAuthenticationPath(properties.getAuthenticationPath(), properties.getPath());
        String realm = properties.getAuthenticationRealm() != null ? properties.getAuthenticationRealm() : null;

        assertIssuerOrAudienceConfigured(properties.getJwtAudience(), properties.getJwtIssuer(),
                properties.isJwtAllowMissingIssuerAndAudience(), "camel.server");

        assertJwtKeystoreHasKey(properties.getJwtKeystoreType(), properties.getJwtKeystorePath(),
                properties.getJwtKeystorePassword(), "camel.server");

        AuthenticationConfigEntry entry = new AuthenticationConfigEntry();
        entry.setPath(path);
        entry.setAuthenticationHandlerFactory(new AuthenticationHandlerFactory() {
            @Override
            public <T extends AuthenticationProvider> AuthenticationHandler createAuthenticationHandler(
                    T authenticationProvider) {
                JWTAuth authProvider = (JWTAuth) authenticationProvider;
                return JWTAuthHandler.create(authProvider, realm);
            }
        });

        entry.setAuthenticationProviderFactory(vertx -> {
            JWTAuthOptions jwtAuthOptions = new JWTAuthOptions(
                    new JsonObject().put("keyStore", new JsonObject()
                            .put("type", properties.getJwtKeystoreType())
                            .put("path", properties.getJwtKeystorePath())
                            .put("password", properties.getJwtKeystorePassword())));

            JWTOptions jwtOptions = buildJwtOptions(properties.getJwtAudience(), properties.getJwtIssuer());
            if (jwtOptions != null) {
                jwtAuthOptions.setJWTOptions(jwtOptions);
            }
            return JWTAuth.create(vertx, jwtAuthOptions);
        });

        authenticationConfig.getEntries().add(entry);
        authenticationConfig.setEnabled(true);
    }

    @Override
    public void configureAuthentication(
            AuthenticationConfig authenticationConfig,
            HttpManagementServerConfigurationProperties properties) {

        String path = resolveAuthenticationPath(properties.getAuthenticationPath(), properties.getPath());
        String realm = properties.getAuthenticationRealm() != null ? properties.getAuthenticationRealm() : null;

        assertIssuerOrAudienceConfigured(properties.getJwtAudience(), properties.getJwtIssuer(),
                properties.isJwtAllowMissingIssuerAndAudience(), "camel.management");

        assertJwtKeystoreHasKey(properties.getJwtKeystoreType(), properties.getJwtKeystorePath(),
                properties.getJwtKeystorePassword(), "camel.management");

        AuthenticationConfigEntry entry = new AuthenticationConfigEntry();
        entry.setPath(path);
        entry.setAuthenticationHandlerFactory(new AuthenticationHandlerFactory() {
            @Override
            public <T extends AuthenticationProvider> AuthenticationHandler createAuthenticationHandler(
                    T authenticationProvider) {
                JWTAuth authProvider = (JWTAuth) authenticationProvider;
                return JWTAuthHandler.create(authProvider, realm);
            }
        });

        entry.setAuthenticationProviderFactory(vertx -> {
            JWTAuthOptions jwtAuthOptions = new JWTAuthOptions(
                    new JsonObject().put("keyStore", new JsonObject()
                            .put("type", properties.getJwtKeystoreType())
                            .put("path", properties.getJwtKeystorePath())
                            .put("password", properties.getJwtKeystorePassword())));

            JWTOptions jwtOptions = buildJwtOptions(properties.getJwtAudience(), properties.getJwtIssuer());
            if (jwtOptions != null) {
                jwtAuthOptions.setJWTOptions(jwtOptions);
            }
            return JWTAuth.create(vertx, jwtAuthOptions);
        });

        authenticationConfig.getEntries().add(entry);
        authenticationConfig.setEnabled(true);
    }

    /**
     * Vert.x only uses the keys whose alias is the name of a JWT algorithm, such as RS256, and reads them with the
     * keystore password. A keystore without such a key loads fine, but then every signed token is rejected, so fail at
     * startup.
     */
    static void assertJwtKeystoreHasKey(String type, String path, String password, String configPrefix) {
        if (path == null) {
            return;
        }
        KeyStore keyStore = loadKeystore(type, path, password, configPrefix);
        String error = checkJwtKeystoreKeys(keyStore, password, configPrefix, path);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    private static KeyStore loadKeystore(String type, String path, String password, String configPrefix) {
        try (InputStream is = openKeystore(path)) {
            if (is == null) {
                throw new IllegalArgumentException(
                        "The JWT keystore " + path + " (" + configPrefix + ".jwtKeystorePath) is not found as a file"
                                                   + " or on the classpath.");
            }
            // Vert.x uses the default keystore type when none is set
            KeyStore keyStore = KeyStore.getInstance(type != null ? type : KeyStore.getDefaultType());
            keyStore.load(is, password != null ? password.toCharArray() : null);
            return keyStore;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException(
                    "Cannot load the JWT keystore " + path + " (" + configPrefix + ".jwtKeystorePath, " + configPrefix
                                               + ".jwtKeystoreType and " + configPrefix + ".jwtKeystorePassword): "
                                               + e.getMessage(),
                    e);
        }
    }

    /**
     * Returns the error for a keystore that has no key Vert.x can use, or null when it has one.
     */
    static String checkJwtKeystoreKeys(KeyStore keyStore, String password, String configPrefix, String path) {
        KeyStore.PasswordProtection protection
                = new KeyStore.PasswordProtection(password != null ? password.toCharArray() : null);
        for (String algorithm : JWT_KEYSTORE_ALIASES) {
            try {
                // as Vert.x does; the alias lookup of a JKS or PKCS12 keystore ignores case
                if (keyStore.getEntry(algorithm, protection) != null) {
                    return null;
                }
            } catch (Exception e) {
                // Vert.x skips an entry it cannot read, such as a key with another password
            }
        }
        String aliases;
        try {
            aliases = Collections.list(keyStore.aliases()).toString();
        } catch (KeyStoreException e) {
            aliases = "unknown";
        }
        return "The JWT keystore " + path + " (" + configPrefix + ".jwtKeystorePath) has no key that Vert.x can use:"
               + " it only reads the keys whose alias is the name of a JWT algorithm ("
               + String.join(", ", JWT_KEYSTORE_ALIASES) + "), with the keystore password (" + configPrefix
               + ".jwtKeystorePassword). Aliases found: " + aliases + ". Rename the alias of the key to the algorithm"
               + " of the key, for example RS256 for an RSA key, with keytool -changealias -alias <alias>"
               + " -destalias RS256.";
    }

    private static InputStream openKeystore(String path) throws IOException {
        Path file = Path.of(path);
        if (Files.isRegularFile(file)) {
            return Files.newInputStream(file);
        }
        // as Vert.x does: the thread context class loader first, then the own one
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        InputStream is = tccl != null ? tccl.getResourceAsStream(path) : null;
        return is != null ? is : JWTAuthenticationConfigurer.class.getClassLoader().getResourceAsStream(path);
    }

    /**
     * A JWT authenticator built without an issuer or an audience only verifies the token signature and the exp/nbf
     * claims, which is weaker than the configuration suggests. Fail closed unless that was asked for explicitly.
     */
    private static void assertIssuerOrAudienceConfigured(
            String audience, String issuer, boolean allowMissing, String configPrefix) {
        if (allowMissing) {
            return;
        }
        if (ObjectHelper.isEmpty(audience) && ObjectHelper.isEmpty(issuer)) {
            throw new IllegalArgumentException(
                    "JWT authentication requires " + configPrefix + ".jwtIssuer or " + configPrefix
                                               + ".jwtAudience to be configured, otherwise tokens are only checked for"
                                               + " signature and expiry. Set " + configPrefix
                                               + ".jwtAllowMissingIssuerAndAudience=true to opt out.");
        }
    }

    private static JWTOptions buildJwtOptions(String audience, String issuer) {

        boolean isAudienceEmpty = ObjectHelper.isEmpty(audience);
        boolean isIssuerEmpty = ObjectHelper.isEmpty(issuer);

        if (isAudienceEmpty && isIssuerEmpty) {
            return null;
        }

        JWTOptions options = new JWTOptions();

        if (!isAudienceEmpty) {
            for (String a : audience.split(",")) {
                if (ObjectHelper.isNotEmpty(a)) {
                    options.addAudience(a.trim());
                }
            }
        }

        if (!isIssuerEmpty) {
            options.setIssuer(issuer);
        }

        return options;
    }
}
