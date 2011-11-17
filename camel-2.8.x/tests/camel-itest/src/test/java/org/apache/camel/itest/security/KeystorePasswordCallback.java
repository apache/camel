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
package org.apache.camel.itest.security;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSPasswordCallback;


public class KeystorePasswordCallback implements CallbackHandler {
    
    private Map<String, String> passwords = 
        new HashMap<String, String>();
    
    public KeystorePasswordCallback() {
        passwords.put("alice", "password");
        passwords.put("jim", "jimspassword");
        passwords.put("bob", "bobspassword");
    }

    /**
     * It attempts to get the password from the private 
     * alias/passwords map.
     */
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (int i = 0; i < callbacks.length; i++) {
            WSPasswordCallback pc = (WSPasswordCallback)callbacks[i];
            String pass = passwords.get(pc.getIdentifier());
            String type = getPasswordType(pc);
            if (WSConstants.PASSWORD_DIGEST.equals(type)) {
                if (pass != null) {
                    pc.setPassword(pass);
                    return;
                }
            } 
            if (WSConstants.PASSWORD_TEXT.equals(type)) {
                // Code for CXF 2.4.X
                if (pc.getPassword() == null) {
                    pc.setPassword(pass);
                    return;
                }
                // Code for CXF 2.3.x
                // As the PasswordType is not PasswordDigest, we need to do the authentication in the call back
                if (!pass.equals(pc.getPassword())) {
                    throw new IOException("Wrong password!");
                }
            }
        }
    }
    
    /**
     * Add an alias/password pair to the callback mechanism.
     */
    public void setAliasPassword(String alias, String password) {
        passwords.put(alias, password);
    }
    
    private String getPasswordType(WSPasswordCallback pc) {
        try {
            Method getType = null;
            try {
                getType = pc.getClass().getMethod("getPasswordType", new Class[0]);
            } catch (NoSuchMethodException ex) {
                // keep looking 
            } catch (SecurityException ex) {
                // keep looking
            }
            if (getType == null) {
                getType = pc.getClass().getMethod("getType", new Class[0]);
            }
            String result = (String)getType.invoke(pc, new Object[0]);
            return result;
            
        } catch (Exception ex) {
            return null;
        }
    }
}
