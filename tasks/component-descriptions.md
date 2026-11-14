# Component and option descriptions (CAMEL-23832)

[CAMEL-23832](https://issues.apache.org/jira/browse/CAMEL-23832) improves the description of every component option, and
the description of what each component does, so that they are clear to people and to AI tools. The same was already done
for the EIPs.

This file tracks which components are done. Anyone, person or agent, can take a component that is not done yet. The
ticket is shared, so leave it unassigned.

## Taking a component

1. Pick a component from the table that has nothing in the "Done" column. Pick at random rather than from the top of the
   table, so that people don't start on the same one.
2. Check the
   [open pull requests for CAMEL-23832](https://github.com/apache/camel/pulls?q=is%3Apr+is%3Aopen+CAMEL-23832), so that
   you don't take a component that is already in progress.
3. Open one pull request per component, with CAMEL-23832 in the title. In the same pull request, write `yes` in the
   "Done" column of the component. If the pull request covers only some of its schemes, write those schemes instead.

## What to change

- Rewrite the option and header descriptions that are wrong, empty, cut off or unclear. Leave a description that is
  correct and clear as it is.
- Put each new text in the `description` attribute of `@UriParam`, `@UriPath` or `@Metadata`, and remove the setter
  Javadoc that held the old text.
- Use the official terms of Java and of the library that the component uses, and describe only what the code does.
- The component description is the Javadoc of the endpoint class, the class annotated with `@UriEndpoint`. Write one
  sentence that says what the component does, starting with a verb such as Send, Receive or Expose, and name no options.
  It is shown in one-line places, such as the component list on the website. The build keeps the Javadoc only up to the
  first line that ends with a period.
- Change only description text: no option names, default values, labels or types.

## Regenerating the files

Build the project once from the root folder with `mvn clean install -Dquickly`. Then run `mvn install -DskipTests` in
these folders, in this order. Do not pass `-Dquickly` here, because it skips the formatter and the DSL generation.

1. the component module
2. `catalog/camel-catalog`
3. `dsl/camel-endpointdsl`
4. `dsl/camel-componentdsl`, if a component option or the component description changed

Commit all generated files: the component JSON and its copy in the catalog, the Endpoint DSL and Component DSL classes
and, if the component description changed, the doc page and its copy in the catalog.

## Components

One row per Maven artifact, with the schemes that it provides.

| Artifact | Schemes | Done |
|---|---|---|
| camel-a2a | a2a |  |
| camel-activemq | activemq |  |
| camel-activemq6 | activemq6 |  |
| camel-ai-prompt | ai-prompt |  |
| camel-ai-resource | ai-resource |  |
| camel-ai-tool | ai-tool |  |
| camel-alibaba-eventbridge | alibaba-eventbridge |  |
| camel-alibaba-fc | alibaba-fc |  |
| camel-alibaba-kms | alibaba-kms |  |
| camel-alibaba-mns | alibaba-mns |  |
| camel-alibaba-oss | alibaba-oss |  |
| camel-alibaba-ots | alibaba-ots |  |
| camel-alibaba-sls | alibaba-sls |  |
| camel-alibaba-sms | alibaba-sms |  |
| camel-amqp | amqp |  |
| camel-apicurio-registry | apicurio-registry |  |
| camel-arangodb | arangodb |  |
| camel-as2 | as2 |  |
| camel-asterisk | asterisk |  |
| camel-atmosphere-websocket | atmosphere-websocket |  |
| camel-atom | atom |  |
| camel-avro-rpc | avro |  |
| camel-aws-bedrock | aws-bedrock, aws-bedrock-agent, aws-bedrock-agent-runtime |  |
| camel-aws-cloudtrail | aws-cloudtrail |  |
| camel-aws-config | aws-config |  |
| camel-aws-parameter-store | aws-parameter-store |  |
| camel-aws-secrets-manager | aws-secrets-manager |  |
| camel-aws-security-hub | aws-security-hub |  |
| camel-aws2-athena | aws2-athena |  |
| camel-aws2-comprehend | aws2-comprehend |  |
| camel-aws2-cw | aws2-cw |  |
| camel-aws2-ddb | aws2-ddb, aws2-ddbstream |  |
| camel-aws2-ec2 | aws2-ec2 |  |
| camel-aws2-ecs | aws2-ecs |  |
| camel-aws2-eks | aws2-eks |  |
| camel-aws2-eventbridge | aws2-eventbridge |  |
| camel-aws2-iam | aws2-iam |  |
| camel-aws2-kinesis | aws2-kinesis, aws2-kinesis-firehose |  |
| camel-aws2-kms | aws2-kms |  |
| camel-aws2-lambda | aws2-lambda |  |
| camel-aws2-mq | aws2-mq |  |
| camel-aws2-msk | aws2-msk |  |
| camel-aws2-polly | aws2-polly |  |
| camel-aws2-redshift | aws2-redshift-data |  |
| camel-aws2-rekognition | aws2-rekognition |  |
| camel-aws2-s3 | aws2-s3 |  |
| camel-aws2-s3-vectors | aws2-s3-vectors |  |
| camel-aws2-ses | aws2-ses |  |
| camel-aws2-sns | aws2-sns |  |
| camel-aws2-sqs | aws2-sqs |  |
| camel-aws2-step-functions | aws2-step-functions |  |
| camel-aws2-sts | aws2-sts |  |
| camel-aws2-textract | aws2-textract |  |
| camel-aws2-timestream | aws2-timestream |  |
| camel-aws2-transcribe | aws2-transcribe |  |
| camel-aws2-translate | aws2-translate |  |
| camel-azure-cosmosdb | azure-cosmosdb |  |
| camel-azure-eventgrid | azure-eventgrid |  |
| camel-azure-eventhubs | azure-eventhubs |  |
| camel-azure-files | azure-files |  |
| camel-azure-functions | azure-functions |  |
| camel-azure-iothub | azure-iothub |  |
| camel-azure-key-vault | azure-key-vault |  |
| camel-azure-servicebus | azure-servicebus |  |
| camel-azure-storage-blob | azure-storage-blob |  |
| camel-azure-storage-datalake | azure-storage-datalake |  |
| camel-azure-storage-queue | azure-storage-queue |  |
| camel-bean | bean, class |  |
| camel-bean-validator | bean-validator |  |
| camel-bonita | bonita |  |
| camel-box | box |  |
| camel-braintree | braintree |  |
| camel-browse | browse |  |
| camel-caffeine | caffeine-cache, caffeine-loadcache |  |
| camel-camunda | camunda |  |
| camel-cassandraql | cql |  |
| camel-cedar | cedar |  |
| camel-chatscript | chatscript |  |
| camel-chunk | chunk |  |
| camel-clickhouse | clickhouse |  |
| camel-clickup | clickup |  |
| camel-cm-sms | cm-sms |  |
| camel-coap | coap, coap+tcp, coaps, coaps+tcp |  |
| camel-cometd | cometd, cometds |  |
| camel-consul | consul |  |
| camel-controlbus | controlbus |  |
| camel-couchbase | couchbase |  |
| camel-couchdb | couchdb |  |
| camel-cron | cron |  |
| camel-crypto | crypto |  |
| camel-cxf-rest | cxfrs | yes |
| camel-cxf-soap | cxf | yes |
| camel-cyberark-vault | cyberark-vault |  |
| camel-dapr | dapr |  |
| camel-dataformat | dataformat |  |
| camel-dataset | dataset, dataset-test |  |
| camel-debezium-db2 | debezium-db2 |  |
| camel-debezium-mongodb | debezium-mongodb |  |
| camel-debezium-mysql | debezium-mysql |  |
| camel-debezium-oracle | debezium-oracle |  |
| camel-debezium-postgres | debezium-postgres |  |
| camel-debezium-sqlserver | debezium-sqlserver |  |
| camel-dfdl | dfdl |  |
| camel-dhis2 | dhis2 |  |
| camel-direct | direct |  |
| camel-disruptor | disruptor, disruptor-vm |  |
| camel-djl | djl |  |
| camel-dns | dns |  |
| camel-docker | docker |  |
| camel-docling | docling |  |
| camel-drill | drill |  |
| camel-dropbox | dropbox |  |
| camel-duckdb | duckdb |  |
| camel-dynamic-router | dynamic-router, dynamic-router-control |  |
| camel-ehcache | ehcache |  |
| camel-elasticsearch | elasticsearch |  |
| camel-elasticsearch-rest-client | elasticsearch-rest-client |  |
| camel-event | event |  |
| camel-exec | exec |  |
| camel-fhir | fhir |  |
| camel-file | file |  |
| camel-file-watch | file-watch |  |
| camel-flatpack | flatpack |  |
| camel-flink | flink |  |
| camel-flowable | flowable |  |
| camel-fop | fop |  |
| camel-freemarker | freemarker |  |
| camel-ftp | ftp, ftps, sftp |  |
| camel-geocoder | geocoder |  |
| camel-git | git |  |
| camel-github2 | github2 |  |
| camel-google-bigquery | google-bigquery, google-bigquery-sql |  |
| camel-google-calendar | google-calendar, google-calendar-stream |  |
| camel-google-drive | google-drive |  |
| camel-google-firestore | google-firestore |  |
| camel-google-functions | google-functions |  |
| camel-google-mail | google-mail, google-mail-stream |  |
| camel-google-pubsub | google-pubsub |  |
| camel-google-secret-manager | google-secret-manager |  |
| camel-google-sheets | google-sheets, google-sheets-stream |  |
| camel-google-speech-to-text | google-speech-to-text |  |
| camel-google-storage | google-storage |  |
| camel-google-text-to-speech | google-text-to-speech |  |
| camel-google-vertexai | google-vertexai |  |
| camel-google-vision | google-vision |  |
| camel-graphql | graphql |  |
| camel-grpc | grpc |  |
| camel-hashicorp-vault | hashicorp-vault |  |
| camel-hazelcast | hazelcast-instance, hazelcast-list, hazelcast-map, hazelcast-multimap, hazelcast-pncounter, hazelcast-queue, hazelcast-replicatedmap, hazelcast-ringbuffer, hazelcast-seda, hazelcast-set, hazelcast-topic |  |
| camel-hibernate | hibernate |  |
| camel-hivemq | hivemq |  |
| camel-http | http, https |  |
| camel-huaweicloud-dms | hwcloud-dms |  |
| camel-huaweicloud-frs | hwcloud-frs |  |
| camel-huaweicloud-functiongraph | hwcloud-functiongraph |  |
| camel-huaweicloud-iam | hwcloud-iam |  |
| camel-huaweicloud-imagerecognition | hwcloud-imagerecognition |  |
| camel-huaweicloud-obs | hwcloud-obs |  |
| camel-huaweicloud-smn | hwcloud-smn |  |
| camel-huggingface | huggingface |  |
| camel-ibm-cos | ibm-cos |  |
| camel-ibm-secrets-manager | ibm-secrets-manager |  |
| camel-ibm-watson-discovery | ibm-watson-discovery |  |
| camel-ibm-watson-language | ibm-watson-language |  |
| camel-ibm-watson-speech-to-text | ibm-watson-speech-to-text |  |
| camel-ibm-watson-text-to-speech | ibm-watson-text-to-speech |  |
| camel-ibm-watsonx-ai | ibm-watsonx-ai |  |
| camel-ibm-watsonx-data | ibm-watsonx-data |  |
| camel-iggy | iggy |  |
| camel-ignite | ignite-cache, ignite-compute, ignite-events, ignite-idgen, ignite-messaging, ignite-queue, ignite-set |  |
| camel-infinispan | infinispan |  |
| camel-infinispan-embedded | infinispan-embedded |  |
| camel-influxdb | influxdb |  |
| camel-influxdb2 | influxdb2 |  |
| camel-jcache | jcache |  |
| camel-jcr | jcr |  |
| camel-jdbc | jdbc |  |
| camel-jetty | jetty |  |
| camel-jgroups | jgroups |  |
| camel-jgroups-raft | jgroups-raft |  |
| camel-jira | jira |  |
| camel-jms | jms |  |
| camel-jmx | jmx |  |
| camel-jolt | jolt |  |
| camel-jooq | jooq |  |
| camel-jpa | jpa |  |
| camel-jsch | scp |  |
| camel-jslt | jslt |  |
| camel-json-validator | json-validator |  |
| camel-jsonata | jsonata |  |
| camel-jt400 | jt400 |  |
| camel-jte | jte |  |
| camel-kafka | kafka |  |
| camel-kafka-share | kafka-share |  |
| camel-kamelet | kamelet |  |
| camel-keycloak | keycloak |  |
| camel-knative | knative |  |
| camel-kserve | kserve |  |
| camel-kubernetes | kubernetes-config-maps, kubernetes-cronjob, kubernetes-custom-resources, kubernetes-deployments, kubernetes-events, kubernetes-hpa, kubernetes-job, kubernetes-namespaces, kubernetes-nodes, kubernetes-persistent-volumes, kubernetes-persistent-volumes-claims, kubernetes-pods, kubernetes-replication-controllers, kubernetes-resources-quota, kubernetes-secrets, kubernetes-service-accounts, kubernetes-services, openshift-build-configs, openshift-builds, openshift-deploymentconfigs |  |
| camel-kudu | kudu |  |
| camel-langchain4j-agent | langchain4j-agent |  |
| camel-langchain4j-chat | langchain4j-chat |  |
| camel-langchain4j-embeddings | langchain4j-embeddings |  |
| camel-langchain4j-embeddingstore | langchain4j-embeddingstore |  |
| camel-langchain4j-ingest | langchain4j-ingest |  |
| camel-langchain4j-web-search | langchain4j-web-search |  |
| camel-language | language |  |
| camel-ldap | ldap |  |
| camel-ldif | ldif |  |
| camel-log | log |  |
| camel-lucene | lucene |  |
| camel-lumberjack | lumberjack |  |
| camel-mail | imap, imaps, pop3, pop3s, smtp, smtps |  |
| camel-mapstruct | mapstruct |  |
| camel-master | master |  |
| camel-metrics | metrics |  |
| camel-micrometer | micrometer |  |
| camel-milo | milo-browse, milo-client, milo-server |  |
| camel-milvus | milvus |  |
| camel-mina | mina |  |
| camel-mina-sftp | mina-sftp |  |
| camel-minio | minio (deprecated) |  |
| camel-mllp | mllp |  |
| camel-mock | mock |  |
| camel-mongodb | mongodb |  |
| camel-mongodb-gridfs | mongodb-gridfs |  |
| camel-mtconnect | mtconnect |  |
| camel-mustache | mustache |  |
| camel-mvel | mvel |  |
| camel-mybatis | mybatis, mybatis-bean |  |
| camel-nats | nats |  |
| camel-neo4j | neo4j |  |
| camel-netty | netty |  |
| camel-netty-http | netty-http |  |
| camel-oaipmh | oaipmh |  |
| camel-odata | odata |  |
| camel-olingo2 | olingo2 (deprecated) |  |
| camel-olingo4 | olingo4 (deprecated) |  |
| camel-once | once |  |
| camel-opa | opa |  |
| camel-openai | llm, openai |  |
| camel-openfeature | openfeature |  |
| camel-openfga | openfga |  |
| camel-opensearch | opensearch |  |
| camel-openstack | openstack-cinder, openstack-glance, openstack-keystone, openstack-neutron, openstack-nova, openstack-swift |  |
| camel-opentelemetry-metrics | opentelemetry-metrics |  |
| camel-optaplanner | optaplanner |  |
| camel-paho | paho (deprecated) |  |
| camel-paho-mqtt5 | paho-mqtt5 |  |
| camel-pdf | pdf |  |
| camel-pg-replication-slot | pg-replication-slot |  |
| camel-pgevent | pgevent |  |
| camel-pgvector | pgvector |  |
| camel-pinecone | pinecone |  |
| camel-platform-http | platform-http |  |
| camel-plc4x | plc4x |  |
| camel-pqc | pqc |  |
| camel-printer | lpr |  |
| camel-pubnub | pubnub |  |
| camel-pulsar | pulsar |  |
| camel-qdrant | qdrant |  |
| camel-quartz | quartz |  |
| camel-quickfix | quickfix |  |
| camel-reactive-streams | reactive-streams |  |
| camel-ref | ref |  |
| camel-rest | rest, rest-api |  |
| camel-rest-openapi | rest-openapi |  |
| camel-rest-postman | rest-postman |  |
| camel-robotframework | robotframework |  |
| camel-rocketmq | rocketmq |  |
| camel-rss | rss |  |
| camel-saga | saga |  |
| camel-salesforce | salesforce |  |
| camel-sap-netweaver | sap-netweaver |  |
| camel-saxon | xquery |  |
| camel-scheduler | scheduler |  |
| camel-schematron | schematron |  |
| camel-seda | seda |  |
| camel-servicenow | servicenow |  |
| camel-servlet | servlet |  |
| camel-shell | shell |  |
| camel-sjms | sjms |  |
| camel-sjms2 | sjms2 |  |
| camel-slack | slack |  |
| camel-smb | smb |  |
| camel-smooks | smooks |  |
| camel-smpp | smpp, smpps |  |
| camel-snmp | snmp |  |
| camel-solr | solr |  |
| camel-spiffe | spiffe |  |
| camel-splunk-hec | splunk-hec |  |
| camel-spring | spring-event |  |
| camel-spring-ai-chat | spring-ai-chat |  |
| camel-spring-ai-embeddings | spring-ai-embeddings |  |
| camel-spring-ai-image | spring-ai-image |  |
| camel-spring-ai-vector-store | spring-ai-vector-store |  |
| camel-spring-batch | spring-batch |  |
| camel-spring-jdbc | spring-jdbc |  |
| camel-spring-ldap | spring-ldap |  |
| camel-spring-rabbitmq | spring-rabbitmq |  |
| camel-spring-redis | spring-redis |  |
| camel-spring-ws | spring-ws |  |
| camel-sql | sql, sql-stored |  |
| camel-ssh | ssh |  |
| camel-state-store | state-store |  |
| camel-stax | stax |  |
| camel-stitch | stitch |  |
| camel-stream | stream |  |
| camel-stringtemplate | string-template |  |
| camel-stripe | stripe |  |
| camel-stub | stub |  |
| camel-tahu | tahu-edge, tahu-host |  |
| camel-telegram | telegram |  |
| camel-tensorflow-serving | tensorflow-serving |  |
| camel-test-citrus | citrus |  |
| camel-thrift | thrift |  |
| camel-thymeleaf | thymeleaf |  |
| camel-tika | tika |  |
| camel-timer | timer |  |
| camel-twilio | twilio |  |
| camel-twitter | twitter-directmessage, twitter-search, twitter-timeline |  |
| camel-typesafe-ai | typesafe-ai |  |
| camel-undertow | undertow |  |
| camel-validator | validator |  |
| camel-velocity | velocity |  |
| camel-vertx | vertx |  |
| camel-vertx-http | vertx-http |  |
| camel-vertx-mqtt | vertx-mqtt |  |
| camel-vertx-websocket | vertx-websocket |  |
| camel-wasm | wasm |  |
| camel-weather | weather |  |
| camel-weaviate | weaviate |  |
| camel-web3j | web3j |  |
| camel-webhook | webhook |  |
| camel-whatsapp | whatsapp |  |
| camel-wordpress | wordpress |  |
| camel-workday | workday |  |
| camel-xchange | xchange |  |
| camel-xj | xj |  |
| camel-xmlsecurity | xmlsecurity-sign, xmlsecurity-verify |  |
| camel-xmpp | xmpp |  |
| camel-xslt | xslt |  |
| camel-xslt-saxon | xslt-saxon |  |
| camel-zendesk | zendesk |  |
| camel-zookeeper | zookeeper |  |
| camel-zookeeper-master | zookeeper-master |  |
