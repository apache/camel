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
package org.apache.camel.updates;

import java.util.function.Supplier;

import org.openrewrite.ExecutionContext;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent of Camel xml visitors, catches any exception, logs it and then continues.
 */
public abstract class AbstractCamelXmlVisitor extends XmlIsoVisitor<ExecutionContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCamelXmlVisitor.class);

    @Override
    public final Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
        return executeVisitWithCatch(() -> doVisitTag(tag, executionContext), tag, executionContext);
    }

    //-------------------------------- internal methods used by children---------------------------------

    public Xml.Tag doVisitTag(Xml.Tag tag, ExecutionContext executionContext) {
        return super.visitTag(tag, executionContext);
    }

    // If the migration fails - do not fail whole migration process, only this one recipe
    protected <T extends Xml> T executeVisitWithCatch(Supplier<T> visitMethod, T origValue, ExecutionContext context) {
        try {
            return visitMethod.get();
        } catch (Exception e) {
            LOGGER.warn(String.format("Internal error detected in %s, recipe is skipped.", getClass().getName()), e);
            return origValue;
        }
    }

}
