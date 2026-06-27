## SQL

This example shows how to use a SQL database with Camel.

The example comes with a `docker compose` file for running a local Postgres database.
There is also a `application.properties` configuration file that setup
a JDBC `DataSource` for connecting to the database.

### Install JBang

First install JBang according to https://www.jbang.dev

When JBang is installed then you should be able to run from a shell:

```sh
$ jbang --version
```

This will output the version of JBang.

To run this example you can either install Camel on JBang via:

```sh
$ jbang app install camel@apache/camel
```

Which allows to run Camel CLI with `camel` as shown below.

### How to run

You can run PostgreSQL using

```sh
$ camel infra run postgres
```

Alternatively, you can run it with Docker Compose:

```sh
$ docker compose up
```

or manually with just Docker

```sh
$ docker run \
--env POSTGRES_DB=test \
--env POSTGRES_USER=postgres \
--env POSTGRES_PASSWORD=postgres \
--publish 5432:5432 \
postgres
```

After Docker starts and pulls down the Postgres image, you can run this example using:

```sh
$ camel run *
```

This runs several routes. The first one sets up a new table called _users_, the second one fills
it with data and the third one runs a query on that table and logs the results.

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
