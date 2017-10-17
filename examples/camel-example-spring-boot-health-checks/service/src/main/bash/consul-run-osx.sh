#!/usr/bin/env bash

CONSUL_VER="1.0.0"
CONSUL_ZIP="consul_${CONSUL_VER}_darwin_amd64.zip"

# cleanup
rm -rf "target/consul-data"
rm -rf "target/consul-config"
rm -rf "target/consul"

mkdir -p target/
mkdir -p target/consul-data
mkdir -p target/consul-config


if [ ! -f target/$CONSUL_ZIP ]; then
    echo Downloading: https://releases.hashicorp.com/consul/$CONSUL_VER/$CONSUL_ZIP 
    curl -o target/$CONSUL_ZIP "https://releases.hashicorp.com/consul/$CONSUL_VER/$CONSUL_ZIP"
fi

cat > target/consul-config/checks.json <<EOF
{
  "checks": [{
    "id": "http", "script": "curl www.google.com >/dev/null 2>&1", "interval": "10s"
  }, {
    "id": "file", "script": "ls /tmp/camel-check >/dev/null 2>&1", "interval": "10s"
  }]
}
EOF

unzip -d target target/$CONSUL_ZIP
chmod +x target/consul

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
    -enable-script-checks \
    -ui
