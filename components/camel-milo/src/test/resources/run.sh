# script which prepares all needed certificates and keystore
# properties of certificates has to be confirmed
# password to be filled in the last step has to be 'test'

mkdir cert
mkdir ca

#generate ca certificate and key
openssl req -x509 -config openssl-ca.cnf -newkey rsa:4096 -sha256 -nodes -out ca/cacert.pem -outform PEM

#generate csr
openssl req -config openssl-server.cnf -newkey rsa:2048 -sha256 -nodes -out cert/servercert.csr -outform PEM

#sign
touch ca/index.txt
echo '01' > ca/serial.txt
openssl ca -config openssl-ca.cnf -policy signing_policy -extensions signing_req -out cert/servercert.pem -infiles cert/servercert.csr

#import into keystore
openssl pkcs12 -export -in cert/servercert.pem -inkey cert/serverkey.pem -certfile ca/cacert.pem -name "test" -out keystore.p12 -passout pass:test
keytool -importkeystore -srckeystore keystore.p12 -srcstoretype pkcs12 -destkeystore keystore -deststoretype JKS -storepass testtest
