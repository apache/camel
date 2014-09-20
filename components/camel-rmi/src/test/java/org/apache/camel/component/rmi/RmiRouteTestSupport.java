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
package org.apache.camel.component.rmi;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;

public abstract class RmiRouteTestSupport extends CamelTestSupport {
    private int port;
    
    protected abstract int getStartPort(); 
    

    protected int getPort() {
        if (port == 0) {
            port = AvailablePortFinder.getNextAvailable(getStartPort());
        }
        return port;
    }
    
    protected boolean classPathHasSpaces() {
        ClassLoader cl = getClass().getClassLoader();
        if (cl instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader)cl;
            URL[] urls = ucl.getURLs();
            for (URL url : urls) {
                if (url.getPath().contains(" ")) {
                    log.error("=======================================================================");
                    log.error(" TEST Skipped: " + this.getClass().getName());
                    log.error("   Your probably on windows.  We detected that the classpath");
                    log.error("   has a space in it.  Try running maven with the following option: ");
                    log.error("   -Dmaven.repo.local=C:\\DOCUME~1\\userid\\.m2\\repository");
                    log.error("=======================================================================");
                    return true;
                }
            }
        }
        return false;
    }

}
