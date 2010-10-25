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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.apache.shiro.util.Factory;

public class ShiroSecurityPolicy implements AuthorizationPolicy {
    private static final transient Log LOG = LogFactory.getLog(ShiroSecurityPolicy.class);  
    private final byte[] bits128 = {
        (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
        (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
        (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
        (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};
    private CipherService cipherService;
    private byte[] passPhrase;
    private SecurityManager securityManager;
    private List<Permission> permissionsList;
    private boolean alwaysReauthenticate;
    
    public ShiroSecurityPolicy() {
        this.passPhrase = bits128;
        // Set up AES encryption based cipher service, by default 
        cipherService = new AesCipherService();
        permissionsList = new ArrayList<Permission>();
        alwaysReauthenticate = true;
    }   
    
    public ShiroSecurityPolicy(String iniResourcePath) {
        this();
        Factory<SecurityManager> factory = new IniSecurityManagerFactory(iniResourcePath);
        securityManager = (SecurityManager) factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);
    }
    
    public ShiroSecurityPolicy(Ini ini) {
        this();
        Factory<SecurityManager> factory = new IniSecurityManagerFactory(ini);
        securityManager = (SecurityManager) factory.getInstance();
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
        //Not implemented
    }
    
    public Processor wrap(RouteContext routeContext, final Processor processor) {        
        return new AsyncProcessor() {
            public boolean process(Exchange exchange, final AsyncCallback callback)  {
                boolean sync;
                try {
                    applySecurityPolicy(exchange);
                } catch (Exception e) {
                    // exception occurred so break out
                    exchange.setException(e);
                    callback.done(true);
                    return true;
                }
                
                // If here, then user is authenticated and authorized
                // Now let the original processor continue routing supporting the async routing engine
                AsyncProcessor ap = AsyncProcessorTypeConverter.convert(processor);
                sync = AsyncProcessorHelper.process(ap, exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // we only have to handle async completion of this policy
                        if (doneSync) {
                            return;
                        }
                        callback.done(false);
                    }
                });                    
                
                if (!sync) {
                    // if async, continue routing async
                    return false;
                }

                // we are done synchronously, so do our after work and invoke the callback
                callback.done(true);
                return true;                
            }

            public void process(Exchange exchange) throws Exception {
                applySecurityPolicy(exchange);               
                processor.process(exchange);
            }
            
            private void applySecurityPolicy(Exchange exchange) throws Exception {
                ByteSource encryptedToken = (ByteSource)exchange.getIn().getHeader("SHIRO_SECURITY_TOKEN");
                ByteSource decryptedToken = getCipherService().decrypt(encryptedToken.getBytes(), getPassPhrase());
                
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decryptedToken.getBytes());
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                ShiroSecurityToken securityToken = (ShiroSecurityToken)objectInputStream.readObject();
                objectInputStream.close();
                byteArrayInputStream.close();
                
                Subject currentUser = SecurityUtils.getSubject();
                
                // Authenticate user if not authenticated
                try {
                    authenticateUser(currentUser, securityToken);
                
                    // Test whether user's role is authorized to perform functions in the permissions list  
                    authorizeUser(currentUser, exchange);
                } finally {
                    if (alwaysReauthenticate) {
                        currentUser.logout();
                        currentUser = null;
                    }
                }

            }
        };
    }

    private void authenticateUser(Subject currentUser, ShiroSecurityToken securityToken) {
        if (!currentUser.isAuthenticated()) {
            UsernamePasswordToken token = new UsernamePasswordToken(securityToken.getUsername(), securityToken.getPassword());
            if (alwaysReauthenticate) {
                token.setRememberMe(false);
            } else {
                token.setRememberMe(true);
            }
            
            try {
                currentUser.login(token);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Current User " + currentUser.getPrincipal() + " successfully authenticated");
                }
            } catch (UnknownAccountException uae) {
                throw new UnknownAccountException("Authentication Failed. There is no user with username of " + token.getPrincipal(), uae.getCause());
            } catch (IncorrectCredentialsException ice) {
                throw new IncorrectCredentialsException("Authentication Failed. Password for account " + token.getPrincipal() + " was incorrect!", ice.getCause());
            } catch (LockedAccountException lae) {
                throw new LockedAccountException("Authentication Failed. The account for username " + token.getPrincipal() + " is locked."
                    + "Please contact your administrator to unlock it.", lae.getCause());
            } catch (AuthenticationException ae) {
                throw new AuthenticationException("Authentication Failed.", ae.getCause());
            }
        }
    }
    
    private void authorizeUser(Subject currentUser, Exchange exchange) throws CamelAuthorizationException {
        boolean authorized = false;
        if (!permissionsList.isEmpty()) {
            for (Permission permission : permissionsList) {
                if (currentUser.isPermitted(permission)) {
                    authorized = true;
                    break;
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Valid Permissions List not specified for ShiroSecurityPolicy. No authorization checks will be performed for current user");
            }
            authorized = true;
        }
        
        if (!authorized) {
            throw new CamelAuthorizationException("Authorization Failed. Subject's role set does not have the necessary permissions to perform further processing", exchange);
        } 
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Current User " + currentUser.getPrincipal() + " is successfully authorized. The exchange will be allowed to proceed");
        }
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
 
}
