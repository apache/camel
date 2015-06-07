# Camel Cassandra Component

This component aims at integrating Cassandra 2.0+ using the CQL3 API (not the Thrift API).
It's based on [Cassandra Java Driver](https://github.com/datastax/java-driver) provided by DataStax.

## URI

### Examples

| URI                              | Description
|----------------------------------|----------------------------------
|`cql:localhost/keyspace`          | single host, default port, usual for testing
|`cql:host1,host2/keyspace`        | multi host, default port
|`cql:host1:host2:9042/keyspace`   |
|`cql:host1:host2`                 | default port and keyspace
|`cql:bean:sessionRef`             | provided Session reference
|`cql:bean:clusterRef/keyspace`    | provided Cluster reference

### Options

| Option                           | Description
|----------------------------------|----------------------------------
|`clusterName`                     | cluster name
|`username and password`           | session authentication
|`cql`                             | CQL query
|`consistencyLevel`                | `ANY`, `ONE`, `TWO`, `QUORUM`, `LOCAL_QUORUM`...
|`resultSetConversionStrategy`     | how is ResultSet converted transformed into message body `ALL`, `ONE`, `LIMIT_10`, `LIMIT_100`...

## Message

### Incoming

Headers:
* `CamelCqlQuery` (optional, String): CQL query

Body
* (`Object[]` or `Collection<Object>`): CQL query parameters to be bound

### Outgoing

Body
* `List<Row>` if resultSetConversionStrategy is ALL or LIMIT_10
* `Row` if resultSetConversionStrategy is ONE
