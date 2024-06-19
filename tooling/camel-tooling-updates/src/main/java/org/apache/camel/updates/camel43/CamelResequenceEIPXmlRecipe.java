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
package org.apache.camel.updates.camel43;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/**
 * <p>
 * The configuration for batch and stream has been renamed from batch-config to batchConfig and stream-config to
 * streamConfig.
 * </p>
 *
 * <p>
 * For example before:
 *
 * <pre>
 *     &lt;resequence&gt;
 *         &lt;stream-config timeout=&quot;1000&quot; deliveryAttemptInterval=&quot;10&quot;/&gt;
 *         &lt;simple&gt;${header.seqnum}&lt;/simple&gt;
 *         &lt;to uri=&quot;mock:result&quot; /&gt;
 *     &lt;/resequence&gt;
 * </pre>
 * </p>
 *
 * <p>
 * And now after:
 *
 * <pre>
 *     &lt;resequence&gt;
 *         &lt;streamConfig timeout=&quot;1000&quot; deliveryAttemptInterval=&quot;10&quot;/&gt;
 *         &lt;simple&gt;${header.seqnum}&lt;/simple&gt;
 *         &lt;to uri=&quot;mock:result&quot; /&gt;
 *     &lt;/resequence&gt;
 * </pre>
 * </p>
 *
 * <p>
 * See the <a href=https://camel.apache.org/manual/camel-4x-upgrade-guide-4_3.html#_resequence_eip>documentation</a>
 * </p>
 */
public class CamelResequenceEIPXmlRecipe extends Recipe {

    private static final XPathMatcher XML_RESEQUENCE_STREAM_CONFIG_MATCHER
            = new XPathMatcher("*/route/resequence/stream-config");
    private static final XPathMatcher XML_RESEQUENCE_BATCH_CONFIG_MATCHER = new XPathMatcher("*/route/resequence/batch-config");

    @Override
    public String getDisplayName() {
        return "Camel Resequence DSL changes";
    }

    @Override
    public String getDescription() {
        return "Batch and stream attributes were renamed in Resequence EIP XML DSL.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {

            @Override
            public Xml.Tag visitTag(final Xml.Tag tag, final ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);

                if (XML_RESEQUENCE_STREAM_CONFIG_MATCHER.matches(getCursor())) {
                    t = t.withName("streamConfig");
                } else if (XML_RESEQUENCE_BATCH_CONFIG_MATCHER.matches(getCursor())) {
                    t = t.withName("batchConfig");
                }

                return t;
            }
        };
    }
}
