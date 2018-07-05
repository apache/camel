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
package org.apache.camel.example.reportincident;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;


/**
 * Callback handler to handle passwords
 */
public class UTPasswordCallback implements CallbackHandler {

    private Map<String, String> passwords = new HashMap<>();

    public UTPasswordCallback() {
        passwords.put("claus", "sualc");
        passwords.put("charles", "selrahc");
        passwords.put("james", "semaj");
        passwords.put("abcd", "dcba");
    }

    /**
     * Here, we attempt to get the password from the private alias/passwords map.
     */
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        String user = "";

        for (Callback callback : callbacks) {
            String pass = passwords.get(getIdentifier(callback));
            if (pass != null) {
                setPassword(callback, pass);
                return;
            }
            
        }

        // Password not found
        throw new IOException("Password does not exist for the user : " + user);
    }
    
    private void setPassword(Callback callback, String pass) {
        try {
            callback.getClass().getMethod("setPassword", String.class).invoke(callback, pass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private String getPassword(Callback callback) {
        try {
            return (String)callback.getClass().getMethod("getPassword").invoke(callback);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getIdentifier(Callback cb) {
        try {
            return (String)cb.getClass().getMethod("getIdentifier").invoke(cb);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add an alias/password pair to the callback mechanism.
     */
    public void setAliasPassword(String alias, String password) {
        passwords.put(alias, password);
    }
    
    private String getPasswordType(Callback pc) {
        try {
            Method getType = null;
            try {
                getType = pc.getClass().getMethod("getPasswordType");
            } catch (NoSuchMethodException ex) {
                // keep looking 
            } catch (SecurityException ex) {
                // keep looking
            }
            if (getType == null) {
                getType = pc.getClass().getMethod("getType");
            }
            String result = (String)getType.invoke(pc);
            return result;
            
        } catch (Exception ex) {
            return null;
        }
    }
}
