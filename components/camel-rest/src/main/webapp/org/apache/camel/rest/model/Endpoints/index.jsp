<%@page contentType="text/html" %>
<%@page pageEncoding="UTF-8" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Endpoints</title>
</head>
<body>

<h2>Endpoints</h2>

<ul>
  <c:forEach var="i" items="${it.endpoints}">
    <li><a href="${i.href}">${i.uri}</a>
  </c:forEach>
</ul>


</body>
</html>
