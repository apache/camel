## Splitter

This example demonstrates the Splitter EIP.

A batch of comma-separated product names is split into individual messages.
Each item is logged with its split index.

The splitter processes each part of the message independently, which is useful
for breaking down bulk data into individual records for downstream processing.

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

You can run this example using:

```sh
$ camel run *
```

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
