## Content Based Router

This example demonstrates the Content Based Router EIP.

A simulated temperature sensor generates random readings between 0 and 40.
The route uses the `choice` EIP to classify each reading:

- **Hot** (>= 30): logged as an alert
- **Normal** (15-29): logged as normal
- **Cold** (< 15): logged as an alert

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
