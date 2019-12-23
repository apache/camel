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
package org.apache.camel.component.graphql.server;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

public final class GraphqlFactory {

    private GraphqlFactory() {
    }

    public static GraphQL newGraphQL() {
        try {
            String schema = IOHelper.loadText(ObjectHelper.loadResourceAsStream("schema.graphqls"));
            GraphQLSchema graphQLSchema = buildSchema(schema);
            return GraphQL.newGraphQL(graphQLSchema).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static GraphQLSchema buildSchema(String schema) {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(schema);
        RuntimeWiring runtimeWiring = buildWiring();
        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    private static RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Query").dataFetcher("books", GraphqlDataFetchers.getBooksDataFetcher()))
            .type(newTypeWiring("Query").dataFetcher("bookById", GraphqlDataFetchers.getBookByIdDataFetcher()))
            .type(newTypeWiring("Book").dataFetcher("author", GraphqlDataFetchers.getAuthorDataFetcher()))
            .type(newTypeWiring("Mutation").dataFetcher("addBook", GraphqlDataFetchers.addBookDataFetcher()))
            .build();
    }

}
