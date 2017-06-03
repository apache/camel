#!/usr/bin/env bash

CONSUL_VER="0.8.1"
CONSUL_ZIP="consul_${CONSUL_VER}_linux_amd64.zip"

# cleanup
rm -rf "target/consul-data"
rm -rf "target/consul-config"
rm -rf "target/consul"

mkdir -p target/
mkdir -p target/consul-data
mkdir -p target/consul-config

if [ ! -f target/${CONSUL_ZIP} ]; then
    wget "https://releases.hashicorp.com/consul/${CONSUL_VER}/${CONSUL_ZIP}" -O target/${CONSUL_ZIP}
fi

cat > target/consul-config/services.json <<EOF
{
  "services": [{
    "id": "s1i1", "name": "service-1", "tags": ["camel", "service-call"], "address": "localhost", "port": 9011
  }, {
    "id": "s1i2", "name": "service-1", "tags": ["camel", "service-call"], "address": "localhost", "port": 9012
  }, {
    "id": "s1i3", "name": "service-1", "tags": ["camel", "service-call"], "address": "localhost", "port": 9013
  }]
}
EOF

unzip -d target target/${CONSUL_ZIP}

target/consul \
    agent \
    -server \
    -bootstrap \
    -datacenter camel \
    -advertise 127.0.0.1 \
    -bind 0.0.0.0 \
    -log-level trace \
    -data-dir target/consul-data \
    -config-dir target/consul-config \
    -ui