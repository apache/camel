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
package org.apache.camel.component.github.producer;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class GitProducerTest extends CamelTestSupport{

	private final static String GIT_LOCAL_REPO = "pippo";
	
    @Override
    public void setUp() throws Exception {
    	super.setUp();
        File localPath = File.createTempFile(GIT_LOCAL_REPO, "");
        localPath.delete();
        File path = new File(GIT_LOCAL_REPO);
        path.deleteOnExit();
    }
    
    @Override
    public void tearDown() throws Exception {
    	super.tearDown();
        File path = new File(GIT_LOCAL_REPO);
        deleteDirectory(path);
    }
    
    @Test
    public void cloneTest() throws Exception {
        template.sendBody("direct:clone","");
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
    }
    
    @Test
    public void initTest() throws Exception {
        template.sendBody("direct:init","");
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {            
            @Override
            public void configure() throws Exception {
                from("direct:clone")
                        .to("git://https://github.com/oscerd/json-webserver-example.git?localPath=" + GIT_LOCAL_REPO + "&operation=clone");
                from("direct:init")
                        .to("git://https://github.com/oscerd/json-webserver-example.git?localPath=" + GIT_LOCAL_REPO + "&operation=init");
            } 
        };
    }
    
    static public boolean deleteDirectory(File path) {
        if( path.exists() ) {
          File[] files = path.listFiles();
          for(int i=0; i<files.length; i++) {
             if(files[i].isDirectory()) {
               deleteDirectory(files[i]);
             }
             else {
               files[i].delete();
             }
          }
        }
        return( path.delete() );
      }
}
