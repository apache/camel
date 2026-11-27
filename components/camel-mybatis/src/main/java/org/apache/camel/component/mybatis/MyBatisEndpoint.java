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
package org.apache.camel.component.mybatis;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.mybatis.MyBatisConstants.SCHEME_MYBATIS;

/**
 * Query, insert, update or delete rows in a relational database, or poll it for rows, with MyBatis mapped statements.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_MYBATIS, title = "MyBatis", syntax = "mybatis:statement",
             category = { Category.DATABASE }, headersClass = MyBatisConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNKNOWN, dependsOn = "outputHeader",
                                   byOption = @BodyType.When(option = "useIterator", value = "false", carriers = Carrier.LIST),
                                   description = "One row of the select per message, as the resultType or resultMap of the"
                                                 + " statement maps it: a bean, a Map or a single value, and null for a row of"
                                                 + " only nulls. With useIterator=false a List of all the rows in one message;"
                                                 + " with outputHeader the row goes in that header and the body is null"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The parameter of the statement, given to MyBatis as it is, without type"
                                                + " conversion: it must fit the parameterType, so the text 123 fails an int"
                                                + " parameter. A bean or a Map for several parameters; no body runs the"
                                                + " statement without one. Insert, Update and Delete run once per element of"
                                                + " a List, an array or an Iterator, the List variants once with the whole"
                                                + " List. With inputHeader the parameter is that header and the body is not"
                                                + " read"),
           producerOut = @BodyType(value = Carrier.UNKNOWN, dependsOn = "outputHeader",
                                   byOption = {
                                           @BodyType.When(option = "statementType", value = "SelectOne",
                                                          carriers = Carrier.UNKNOWN),
                                           @BodyType.When(option = "statementType", value = "SelectList",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "statementType", value = "Insert",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "statementType", value = "InsertList",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "statementType", value = "Update",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "statementType", value = "UpdateList",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "statementType", value = "Delete",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "statementType", value = "DeleteList",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "By the statementType, which the producer needs: SelectOne gives the one"
                                                 + " row as the statement maps it (a bean, a Map or a value), or null for no"
                                                 + " row; SelectList a List of the rows. Insert, Update and Delete leave the"
                                                 + " body unchanged, with the count of rows in the CamelMyBatisResult header."
                                                 + " With outputHeader the result goes in that header and the body is"
                                                 + " unchanged"))
public class MyBatisEndpoint extends BaseMyBatisEndpoint {

    @UriPath(description = "The ID of the mapped statement to run, as a mapper XML file or a mapper annotation of the MyBatis"
                           + " configuration declares it, for example selectAllAccounts, or qualified by its namespace. The"
                           + " consumer runs it as a select without parameter on each poll, and the producer as the"
                           + " statementType option says.")
    @Metadata(required = true)
    private String statement;
    @UriParam(label = "producer",
              description = "The kind of operation that the producer runs the statement as. The producer requires it."
                            + " SelectOne fails when the select returns more than one row. Insert, Update and Delete run the"
                            + " statement once per element when the parameter is a Collection, an array or an Iterator, while"
                            + " InsertList, UpdateList and DeleteList run it once with the whole List, for a foreach in the"
                            + " statement.")
    private StatementType statementType;
    @UriParam(label = "consumer",
              description = "Whether the consumer stops processing the other rows of the poll when an exchange fails or is"
                            + " marked rollback only, and fails the poll with that exception. It does not start a transaction:"
                            + " the select and the onConsume statements each run in their own MyBatis session.")
    private boolean transacted;
    @UriParam(label = "consumer", defaultValue = "0",
              description = "The maximum number of exchanges that the consumer processes in one poll. The select still reads"
                            + " all the rows, the other rows are not routed in that poll, and the next poll runs the select"
                            + " again. A value of 0 or less means no limit.")
    private int maxMessagesPerPoll;
    @UriParam(label = "consumer",
              description = "The ID of the mapped statement that the consumer runs as an update after an exchange is"
                            + " processed successfully, with the row (or the List of rows) as parameter, for example to mark"
                            + " the row as processed. Separate several statements with commas. A failed or rollback only"
                            + " exchange does not run it, so the next poll selects the row again.")
    private String onConsume;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the consumer routes each row of the select in its own exchange. If false, it routes all"
                            + " the rows of a poll as one List in one exchange.")
    private boolean useIterator = true;
    @UriParam(label = "consumer",
              description = "Whether the consumer routes an exchange with an empty List when the select returns no rows. It"
                            + " applies only when useIterator is false; otherwise no rows means no exchange.")
    private boolean routeEmptyResultSet;
    @UriParam(label = "consumer,advanced",
              description = "A custom org.apache.camel.component.mybatis.MyBatisProcessingStrategy that runs the select of a"
                            + " poll and the onConsume statements. The default runs the select with selectList and each"
                            + " onConsume statement with update, each in its own SqlSession.")
    private MyBatisProcessingStrategy processingStrategy = new DefaultMyBatisProcessingStrategy();

    public MyBatisEndpoint() {
    }

    public MyBatisEndpoint(String endpointUri, Component component, String statement) {
        super(endpointUri, component);
        this.statement = statement;
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(statementType, "statementType", this);
        ObjectHelper.notNull(statement, "statement", this);
        return new MyBatisProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(statement, "statement", this);
        MyBatisConsumer consumer = new MyBatisConsumer(this, processor);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        configureConsumer(consumer);
        return consumer;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public void setStatementType(StatementType statementType) {
        this.statementType = statementType;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    public MyBatisProcessingStrategy getProcessingStrategy() {
        return processingStrategy;
    }

    public void setProcessingStrategy(MyBatisProcessingStrategy processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public String getOnConsume() {
        return onConsume;
    }

    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    public boolean isUseIterator() {
        return useIterator;
    }

    public void setUseIterator(boolean useIterator) {
        this.useIterator = useIterator;
    }

    public boolean isRouteEmptyResultSet() {
        return routeEmptyResultSet;
    }

    public void setRouteEmptyResultSet(boolean routeEmptyResultSet) {
        this.routeEmptyResultSet = routeEmptyResultSet;
    }
}
