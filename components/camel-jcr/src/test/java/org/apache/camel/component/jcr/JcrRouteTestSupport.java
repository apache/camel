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
package org.apache.camel.component.jcr;

import java.io.File;
import java.io.FileNotFoundException;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jackrabbit.core.TransientRepository;
import org.junit.Before;

/**
 * JcrRouteTestSupport
 */
public abstract class JcrRouteTestSupport extends CamelTestSupport {

    protected static final String CONFIG_FILE = "target/test-classes/repository-simple-security.xml";

    protected static final String REPO_PATH = "target/repository-simple-security";

    private Repository repository;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(REPO_PATH);
        super.setUp();
    }

    protected Repository getRepository() {
        return repository;
    }

    protected Session openSession() throws RepositoryException {
        return getRepository().login(new SimpleCredentials("user", "pass".toCharArray()));
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        File config = new File(CONFIG_FILE);
        if (!config.exists()) {
            throw new FileNotFoundException("Missing config file: " + config.getPath());
        }
        
        repository = new TransientRepository(CONFIG_FILE, REPO_PATH);
        registry.bind("repository", repository);
    }
}
