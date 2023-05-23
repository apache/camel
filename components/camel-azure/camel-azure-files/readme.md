# Camel Azure Files Component

This component allows to access Azure Files over public Internet.
It reasonably mimics the (S)FTP(S) components.
It uses Azure Java SDK.

## State

In development since 2023-05, so far experimental.

## History

Formulated requirement in https://issues.apache.org/jira/browse/CAMEL-19279.

Started be blending FTP component and camel-azure-storage-blob.

FTPFile is replaced by ShareFileItem.

Adapted connection management in the operations class. Azure Java SDK
manages connections internally. It might be possible to integrate
down at the connection provider level but we are not targeting it upfront
because FTP protocol uses another session and connection(s) management
concept than HTTP-based protocols. 

Initially I had hoped, I could reuse some classes as-is but in
reality a copy-and-paste has been needed because of FTPFile use
(despite impl needs only: file name, last modified and length). 

## Deps

At first I want to use the component with Camel 3.16+ hence
Java 11 is selected as a base dependency.

https://camel.apache.org/manual/what-are-the-dependencies.html

Java 17, and hence Camel 4 port, might come later once Java 17 is
approved by our security team.
