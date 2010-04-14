#!/bin/bash
rm -rf work
mkdir work
wget --http-user=jim --http-password=jimspassword --directory-prefix=work  --output-file=work/log.txt http://localhost:8080/camel/admin
cat -n work/* | less
