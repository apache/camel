PgEvent Component 
=================

This is a component for [Apache Camel](http://camel.apache.org/) which allows
for Producing/Consuming PostgreSQL events related to the LISTEN/NOTIFY commands
added since PostgreSQL 8.3.

## Usage (Not yet working)

You can configure this component via URI parameters like most other Camel 
components. The possible parameters are listed below:

    String host = "localhost";
    
    Integer port = 5432;
    
    String database;
    
    String channel;
    
    String user = "postgres";
    
    String pass;
    
    DataSource datasource;

If you use the pgDataSource parameter, all other *connection* parameters are 
ignored. The *channel* parameter, however, is always required.

## URI Format

    pgevent:[datasource] || [//host:port]/<database>/<channel>[?parameters]
    
    pgevent:myDataSource/proddb/userupdates
    
    pgevent://192.168.1.12:5432/proddb/groupupdates?user=username&pass=secret
    
    pgevent://192.168.1.12/proddb/groupupdates?user=username&pass=secret
    
    pgevent:///proddb/customerupdates   ## Uses all defaults


## Building And Installing

To build this project use

    mvn install

For more help see the Apache Camel documentation:

    http://camel.apache.org/writing-components.html
