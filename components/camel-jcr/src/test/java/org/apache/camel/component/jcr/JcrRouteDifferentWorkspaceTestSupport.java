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
package org.apache.camel.component.jcr;

import java.io.File;
import java.io.FileNotFoundException;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import javax.naming.Context;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jackrabbit.core.TransientRepository;
import org.junit.Before;

/**
 * JcrRouteDifferentWorkspaceTestSupport
 * 
 */
public abstract class JcrRouteDifferentWorkspaceTestSupport extends CamelTestSupport {

    protected static final String CONFIG_FILE = "target/test-classes/repository-simple-security.xml";

    protected static final String REPO_PATH = "target/repository-simple-security";
    
    protected static final String CUSTOM_WORKSPACE_NAME = "testWorkspace";

    private Repository repository;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(REPO_PATH);
        super.setUp();
        Session session = getRepository().login(new SimpleCredentials("user", "pass".toCharArray()));
        Workspace workspace = session.getWorkspace();
        workspace.createWorkspace(CUSTOM_WORKSPACE_NAME);
        session.save();
        session.logout();
    }

    protected Repository getRepository() {
        return repository;
    }
    
    protected Session openSession(String workspaceName) throws RepositoryException {
        return getRepository().login(new SimpleCredentials("user", "pass".toCharArray()), workspaceName);
    }

    @Override
    protected Context createJndiContext() throws Exception {
        File config = new File(CONFIG_FILE);
        if (!config.exists()) {
            throw new FileNotFoundException("Missing config file: " + config.getPath());
        }
        
        Context context = super.createJndiContext();
        repository = new TransientRepository(CONFIG_FILE, REPO_PATH);
        context.bind("repository", repository);
        return context;
    }
}
