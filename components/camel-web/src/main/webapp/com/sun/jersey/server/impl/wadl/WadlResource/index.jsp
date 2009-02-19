<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Camel REST API</title>
</head>
<body>

<h1>Camel REST API</h1>

<p>
  Camel supports a RESTful API for browsing and interacting with endpoints and routes to create and modify your
  <a href="http://camel.apache.org/enterprise-integration-patterns.html">Enterprise Integration Patterns</a>.
</p>

<p>
  Most resources are available at the very least as HTML, XML and JSON formats with some other formats being available.
  Your web browser will serve up the HTML representation by default unless you specify the HTTP <code>Accept</code>
  header
  with <code>text/xml</code> or <code>application/xml</code> for XML and <code>application/json</code> for JSON.
  Though you can typically add <b>.xml</b> or <b>.json</b> to a URI to get the XML or JSON respresentation in your browser
  without having to mess around with <code>Accept</code> headers.
</p>

<api:resource resource="${it.rootResource}"/>

</body>
</html>
