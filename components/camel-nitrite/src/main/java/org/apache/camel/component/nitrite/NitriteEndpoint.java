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
package org.apache.camel.component.nitrite;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.PersistentCollection;

/**
 * Used for integrating Camel with Nitrite databases.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "nitrite", title = "Nitrite", syntax = "nitrite:database",
             label = "database,nosql")
public class NitriteEndpoint extends DefaultEndpoint {
    @UriPath(label = "common", description = "Path to database file. Will be created if not exists.")
    @Metadata(required = true)
    private String database;
    @UriParam(label = "common", description = "Name of Nitrite collection. "
            + "Cannot be used in combination with repositoryClass option.")
    private String collection;
    @UriParam(label = "common", description = "Class of Nitrite ObjectRepository. "
            + "Cannot be used in combination with collection option.")
    private Class<?> repositoryClass;
    @UriParam(label = "common", description = "Optional name of ObjectRepository. "
            + "Can be only used in combination with repositoryClass, otherwise have no effect")
    private String repositoryName;
    @UriParam(label = "security", description = "Username for Nitrite database. "
            + "Database is not secured if option not specified.")
    private String username;
    @UriParam(label = "security", description = "Password for Nitrite database. Required, if option username specified.")
    private String password;

    private Nitrite nitriteDatabase;
    private PersistentCollection nitriteCollection;

    public NitriteEndpoint() {
    }

    public NitriteEndpoint(String uri, NitriteComponent component) {
        super(uri, component);
    }

    public void setNitriteCollection(PersistentCollection collection) {
        this.nitriteCollection = collection;
    }

    public void setNitriteDatabase(Nitrite nitriteDatabase) {
        this.nitriteDatabase = nitriteDatabase;
    }

    public Producer createProducer() throws Exception {
        return new NitriteProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new NitriteConsumer(this, processor);
    }

    @Override
    public NitriteComponent getComponent() {
        return (NitriteComponent)super.getComponent();
    }

    public PersistentCollection getNitriteCollection() {
        return nitriteCollection;
    }

    public Nitrite getNitriteDatabase() {
        return nitriteDatabase;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDatabase() {
        return database;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Class<?> getRepositoryClass() {
        return repositoryClass;
    }

    public void setRepositoryClass(Class<?> repositoryClass) {
        this.repositoryClass = repositoryClass;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }
}
