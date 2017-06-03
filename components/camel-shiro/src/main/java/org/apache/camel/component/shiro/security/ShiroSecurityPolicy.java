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
package org.apache.camel.component.shiro.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShiroSecurityPolicy implements AuthorizationPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(ShiroSecurityPolicy.class);
    private final byte[] bits128 = {
        (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
        (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
        (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
        (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};
    private CipherService cipherService;
    private byte[] passPhrase;
    private SecurityManager securityManager;
    private List<Permission> permissionsList;
    private List<String> rolesList;
    private boolean alwaysReauthenticate;
    private boolean base64;
    private boolean allPermissionsRequired;
    private boolean allRolesRequired;
    
    public ShiroSecurityPolicy() {
        this.passPhrase = bits128;
        // Set up AES encryption based cipher service, by default 
        cipherService = new AesCipherService();
        permissionsList = new ArrayList<Permission>();
        rolesList = new ArrayList<String>();
        alwaysReauthenticate = true;
    }   
    
    public ShiroSecurityPolicy(String iniResourcePath) {
        this();
        Factory<SecurityManager> factory = new IniSecurityManagerFactory(iniResourcePath);
        securityManager = factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);
    }
    
    public ShiroSecurityPolicy(Ini ini) {
        this();
        Factory<SecurityManager> factory = new IniSecurityManagerFactory(ini);
        securityManager = factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);
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
    
    public ShiroSecurityPolicy(String iniResourcePath, byte[] passPhrase, boolean alwaysReauthenticate, List<Permission> permissionsList) {
        this(iniResourcePath, passPhrase, alwaysReauthenticate); 
        this.setPermissionsList(permissionsList);
    }
    
    public ShiroSecurityPolicy(Ini ini, byte[] passPhrase, boolean alwaysReauthenticate, List<Permission> permissionsList) {
        this(ini, passPhrase, alwaysReauthenticate); 
        this.setPermissionsList(permissionsList);
    }

    public void beforeWrap(RouteContext routeContext, ProcessorDefinition<?> definition) {  
        // noop
    }
    
    public Processor wrap(RouteContext routeContext, final Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} using Shiro policy {}", routeContext.getRoute().getId(), this);
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
