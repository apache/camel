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
package org.apache.camel.component.xmlsecurity.processor;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.security.Security;

import org.apache.jcp.xml.dsig.internal.dom.XMLDSigRI;
import org.apache.xml.security.utils.XMLUtils;

public final class SantuarioUtil {
    private SantuarioUtil() {
        //Helper class
    }

    public static void initializeSantuario() {
        // Set ignoreLineBreaks to true
        boolean wasSet = false;
        try {
            // Don't override if it was set explicitly
            wasSet = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String lineBreakPropName = "org.apache.xml.security.ignoreLineBreaks";
                    if (System.getProperty(lineBreakPropName) == null) {
                        System.setProperty(lineBreakPropName, "true");
                        return false;
                    }
                    return true; 
                }
            });
        } catch (Throwable t) {
            //ignore
        }
        org.apache.xml.security.Init.init();
        if (!wasSet) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
                    public Boolean run() throws Exception {
                        Field f = XMLUtils.class.getDeclaredField("ignoreLineBreaks");
                        f.setAccessible(true);
                        f.set(null, Boolean.TRUE);
                        return false;
                    }
                });
            } catch (Throwable t) {
                //ignore
            }
        }
    }
    
    public static void addSantuarioJSR105Provider() {
        AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                String providerName = "ApacheXMLDSig";
                Provider currentProvider = Security.getProvider(providerName);
                if (currentProvider != null) {
                    Security.removeProvider(currentProvider.getName());
                }
                Security.addProvider(new XMLDSigRI());
                return true;
            }
        });
    }
    
}
