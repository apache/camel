<%--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
--%>
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
  <title><decorator:title default="Camel Console"/></title>
  <style type="text/css" media="screen">
    @import url(<c:url value="/css/sorttable.css"/>);
    @import url(<c:url value="/css/type-settings.css"/>);
    @import url(<c:url value="/css/site.css"/>);
  </style>
  <%
      if (request.getAttribute("noJavaScript") == null) {
  %>
    <script type='text/javascript' src='<c:url value="/js/common.js"/>'></script>
    <script type='text/javascript' src='<c:url value="/js/css.js"/>'></script>
    <script type='text/javascript' src='<c:url value="/js/standardista-table-sorting.js"/>'></script>
  <%
    }
  %>

  <decorator:head/>
</head>

<%--
<body onload='<decorator:getProperty property="body.onload"/>'>
--%>
<body>


<div class="white_box">
  <div class="header">
    <div class="header_l">
      <div class="header_r">
      </div>
    </div>
  </div>
  <div class="content">
    <div class="content_l">
      <div class="content_r">

        <div>

          <!-- Banner -->
          <div id="asf_logo">
            <div id="activemq_logo">
              <a style="float:left; width:280px;display:block;text-indent:-5000px;text-decoration:none;line-height:60px; margin-top:10px; margin-left:100px;"
                 href="http://camel.apache.org/"
                 title="a powerful open source integration framework based on known Enterprise Integration Patterns with powerful Bean Integration">Camel</a>
              <a style="float:right; width:210px;display:block;text-indent:-5000px;text-decoration:none;line-height:60px; margin-top:15px; margin-right:10px;"
                 href="http://www.apache.org/" title="The Apache Software Foundation">ASF</a>
            </div>
          </div>


          <div class="top_red_bar">
            <div id="site-breadcrumbs">
              <a href="<c:url value='/index'/>" title="Home">Home</a>
              &#124;
              <a href="<c:url value='/endpoints'/>" title="View current endpoints or create new ones">Endpoints</a>
              &#124;
              <a href="<c:url value='/routes'/>" title="View current routes">Routes</a>
            </div>
            <div id="site-quicklinks"><P>
              <a href="http://camel.apache.org/support.html"
                 title="Get help and support using Apache Camel">Support</a></p>
            </div>
          </div>

          <table border="0">
            <tbody>
            <tr>
              <td valign="top" width="100%" style="overflow:hidden;">
                <div class="body-content">
                  <decorator:body/>
                </div>
              </td>
              <td valign="top">

                <div class="navigation">
                  <div class="navigation_top">
                    <div class="navigation_bottom">

                      <%--
                                            <H3>Actions</H3>

                                            <ul class="alternate" type="square">
                                              <li></li>
                                            </ul>


                      --%>
                      <H3>Useful Links</H3>

                      <ul class="alternate" type="square">
                        <li><a href="http://camel.apache.org/documentation.html"
                               title="a powerful open source integration framework based on known Enterprise Integration Patterns with powerful Bean Integration">Documentation</a>
                        </li>
                        <li><a href="http://camel.apache.org/web-console.html"
                               title="more help on using the Web Console">Console Help</a>
                        </li>
                        <li><a href="http://camel.apache.org/faq.html">FAQ</a></li>
                        <li><a href="<c:url value='/api'/>" title="View the REST API details">API</a>
                        </li>
                        <li><a href="http://camel.apache.org/download.html">Downloads</a>
                        </li>
                        <li><a href="http://camel.apache.org/discussion-forums.html">Forums</a>
                        </li>
                      </ul>
                    </div>
                  </div>
                </div>
              </td>
            </tr>
            </tbody>
          </table>


          <div class="bottom_red_bar"></div>
        </div>
      </div>
    </div>
  </div>
  <div class="black_box">
    <div class="footer">
      <div class="footer_l">
        <div class="footer_r">
          <div>
            Copyright 2005-2009 The Apache Software Foundation.

            (<a href="?printable=true">printable version</a>)
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
<div class="design_attribution"><a href="http://hiramchirino.com/">Graphic Design By Hiram</a></div>

</body>
</html>

