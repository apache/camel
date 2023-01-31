#!/bin/bash
set -e

keytool -genkey -keystore server-side-keystore.jks -storepass password -keypass password -dname "CN=localhost, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -keyalg RSA
keytool -export -keystore server-side-keystore.jks -file server-side-cert.cer -storepass password
keytool -import -keystore client-side-truststore.jks -file server-side-cert.cer -storepass password -keypass password -noprompt
keytool -genkey -keystore client-side-keystore.jks -storepass password -keypass password -dname "CN=ActiveMQ Artemis Client, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -keyalg RSA
keytool -export -keystore client-side-keystore.jks -file client-side-cert.cer -storepass password
keytool -import -keystore server-side-truststore.jks -file client-side-cert.cer -storepass password -keypass password -noprompt