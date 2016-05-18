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
package org.apache.camel.component.javaspace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @version 
 */
public final class Utility {

    private Utility() {
    }
    
    public static synchronized void setSecurityPolicy(String policyResourceName, String tmpFileName) throws IOException {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(policyResourceName);
        if (in == null) {
            throw new IOException("Unable to find the resource policy.all on classpath");
        }
        File outfile = new File(tmpFileName);
        OutputStream out = new FileOutputStream(outfile);

        byte[] tmp = new byte[8192];
        int len = 0;
        while (true) {
            len = in.read(tmp);
            if (len <= 0) {
                break;
            }
            out.write(tmp, 0, len);
        }
        out.close();
        in.close();
        System.setProperty("java.security.policy", outfile.getAbsolutePath());
    }

}
