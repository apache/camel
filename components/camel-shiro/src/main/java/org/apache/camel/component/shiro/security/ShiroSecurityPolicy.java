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
package org.apache.camel.component.shiro.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.config.Ini;
import org.apache.shiro.crypto.cipher.AesCipherService;
import org.apache.shiro.crypto.cipher.CipherService;
import org.apache.shiro.env.BasicIniEnvironment;
import org.apache.shiro.mgt.SecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Metadata(label = "bean",
          description = "An authorization policy that authenticates the caller with Apache Shiro, from the encrypted"
                        + " ShiroSecurityToken sent with the message, and checks its permissions and roles. Create it with"
                        + " the Shiro INI resource as its constructor argument, or set securityManager.",
          annotations = { "interfaceName=org.apache.camel.spi.AuthorizationPolicy" })
@Configurer(metadataOnly = true)
public class ShiroSecurityPolicy implements AuthorizationPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(ShiroSecurityPolicy.class);
    @Metadata(label = "advanced",
              description = "The cipher service that decrypts the ShiroSecurityToken sent with the message. AES by default;"
                            + " Shiro also ships a Blowfish one.")
    private CipherService cipherService;
    @Metadata(label = "security", security = "secret",
              description = "The key the ShiroSecurityToken sent with the message is decrypted with.")
    private byte[] passPhrase;
    @Metadata(description = "The Shiro security manager that authenticates and authorizes the caller. The constructors build"
                            + " it from a Shiro INI resource; set it when the security manager comes from elsewhere.")
    private SecurityManager securityManager;
    @Metadata(description = "The permissions an authenticated caller needs to continue on the route. Any one of them is"
                            + " enough unless allPermissionsRequired is set. When they are set, rolesList is not checked.")
    private List<Permission> permissionsList;
    @Metadata(description = "The roles an authenticated caller needs to continue on the route, checked only when"
                            + " permissionsList is empty. Any one of them is enough unless allRolesRequired is set.")
    private List<String> rolesList;
    @Metadata(defaultValue = "true",
              description = "Whether to log the caller out after every exchange. When false, the authenticated subject"
                            + " stays bound to the worker thread for the next exchange on that thread; the token of every"
                            + " exchange is still authenticated.")
    private boolean alwaysReauthenticate;
    @Metadata(defaultValue = "false",
              description = "Whether the ShiroSecurityToken header is a base64 string rather than the token object, which lets"
                            + " it travel over JMS and other transports.")
    private boolean base64;
    @Metadata(defaultValue = "false",
              description = "Whether the caller needs all the permissions in permissionsList, rather than any one of them.")
    private boolean allPermissionsRequired;
    @Metadata(defaultValue = "false",
              description = "Whether the caller needs all the roles in rolesList, rather than any one of them.")
    private boolean allRolesRequired;

    public ShiroSecurityPolicy() {
        // Set up AES encryption based cipher service, by default
        cipherService = new AesCipherService();
        permissionsList = new ArrayList<>();
        rolesList = new ArrayList<>();
        alwaysReauthenticate = true;
    }

    public ShiroSecurityPolicy(String iniResourcePath) {
        this();
        Ini ini = new Ini();
        ini.loadFromPath(iniResourcePath);
        securityManager = new BasicIniEnvironment(ini).getSecurityManager();
    }

    public ShiroSecurityPolicy(Ini ini) {
        this();
        securityManager = new BasicIniEnvironment(ini).getSecurityManager();
    }

    public ShiroSecurityPolicy(String iniResourcePath, byte[] passPhrase) {
        this(iniResourcePath);
        this.setPassPhrase(passPhrase);
    }

    public ShiroSecurityPolicy(Ini ini, byte[] passPhrase) {
        this(ini);
        this.setPassPhrase(passPhrase);
    }

    public ShiroSecurityPolicy(String iniResourcePath, byte[] passPhrase, boolean alwaysReauthenticate) {
        this(iniResourcePath, passPhrase);
        this.setAlwaysReauthenticate(alwaysReauthenticate);
    }

    public ShiroSecurityPolicy(Ini ini, byte[] passPhrase, boolean alwaysReauthenticate) {
        this(ini, passPhrase);
        this.setAlwaysReauthenticate(alwaysReauthenticate);
    }

    public ShiroSecurityPolicy(String iniResourcePath, byte[] passPhrase, boolean alwaysReauthenticate,
                               List<Permission> permissionsList) {
        this(iniResourcePath, passPhrase, alwaysReauthenticate);
        this.setPermissionsList(permissionsList);
    }

    public ShiroSecurityPolicy(Ini ini, byte[] passPhrase, boolean alwaysReauthenticate, List<Permission> permissionsList) {
        this(ini, passPhrase, alwaysReauthenticate);
        this.setPermissionsList(permissionsList);
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        // noop
    }

    @Override
    public Processor wrap(Route route, final Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} using Shiro policy {}", route.getRouteId(), this);
        }
        return new ShiroSecurityProcessor(processor, this);
    }

    public CipherService getCipherService() {
        return cipherService;
    }

    public void setCipherService(CipherService cipherService) {
        this.cipherService = cipherService;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public void setSecurityManager(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public byte[] getPassPhrase() {
        return passPhrase;
    }

    public void setPassPhrase(byte[] passPhrase) {
        this.passPhrase = passPhrase;
    }

    public List<Permission> getPermissionsList() {
        return permissionsList;
    }

    public void setPermissionsList(List<Permission> permissionsList) {
        this.permissionsList = permissionsList;
    }

    public boolean isAlwaysReauthenticate() {
        return alwaysReauthenticate;
    }

    public void setAlwaysReauthenticate(boolean alwaysReauthenticate) {
        this.alwaysReauthenticate = alwaysReauthenticate;
    }

    public boolean isBase64() {
        return base64;
    }

    public void setBase64(boolean base64) {
        this.base64 = base64;
    }

    public boolean isAllPermissionsRequired() {
        return allPermissionsRequired;
    }

    public void setAllPermissionsRequired(boolean allPermissionsRequired) {
        this.allPermissionsRequired = allPermissionsRequired;
    }

    public List<String> getRolesList() {
        return rolesList;
    }

    public void setRolesList(List<String> rolesList) {
        this.rolesList = rolesList;
    }

    public boolean isAllRolesRequired() {
        return allRolesRequired;
    }

    public void setAllRolesRequired(boolean allRolesRequired) {
        this.allRolesRequired = allRolesRequired;
    }
}
