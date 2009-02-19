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
</p>

<table>
  <tr>
    <th>URI</th>
    <th>Description</th>
    <th>Related Resources</th>
  </tr>
  <tr>
    <td><a href="<c:url value="/"/>">/</a>
    </td>
    <td>
      Summary links to other resources
    </td>
    <td>
      <ul>
        <li>
          <a href="<c:url value="/index.xml"/>">/index.xml</a> for XML
        </li>
        <li>
          <a href="<c:url value="/index.json"/>">/index.json</a> for JSON
        </li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><a href="<c:url value="/endpoints"/>">/endpoints</a></td>
    <td>
      The currently active endpoints
    </td>
    <td>
      <ul>
        <li>
          <a href="<c:url value="/endpoints.xml"/>">/endpoints.xml</a> for XML
        </li>
        <li>
          <a href="<c:url value="/endpoints.json"/>">/endpoints.json</a> for JSON
        </li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><a href="<c:url value="/routes"/>">/routes</a></td>
    <td>
      The currently active routes
    </td>
    <td>
      <ul>
        <li>
          <a href="<c:url value="/routes.xml"/>">/routes.xml</a> for XML
        </li>
        <li>
          <a href="<c:url value="/routes.json"/>">/routes.json</a> for JSON
        </li>
        <li>
          <a href="<c:url value="/routes.dot"/>">/routes.dot</a> for a <a href="http://graphviz.org/">Graphviz</a>
          DOT file for <a href="http://camel.apache.org/visualisation.html">visualising your routes</a>.
        </li>
      </ul>
    </td>
  </tr>
  <tr>
    <td><a href="<c:url value="/application.wadl"/>">/application.wadl</a></td>
    <td>
      The <a href="https://wadl.dev.java.net/">WADL</a> description of all the available resources
    </td>
    <td>
    </td>
  </tr>
</table>
</body>
</html>
