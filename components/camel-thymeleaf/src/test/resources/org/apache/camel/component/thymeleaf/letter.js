/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
<!--/*
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

    Comments are not supported in JAVASCRIPT templates, so this ASF header will appear in the rendered text.
*/-->
<span th:fragment="show-hide-content" th:remove="tag">
    <script th:inline="javascript">
        function hideContent() {
            showContentButton = document.getElementById(/*[[${headers.showContentButton}]]*/ 'showContentButton');
            hideContentButton = document.getElementById(/*[[${headers.hideContentButton}]]*/ 'hideContentButton');
            someContent = document.getElementById(/*[[${body}]]*/ 'someContent');

            showContentButton.style.display = 'block';
            hideContentButton.style.display = 'none';
            someContent.style.display = 'none';
        }

        function showContent() {
            showContentButton = document.getElementById(/*[[${headers.showContentButton}]]*/ 'showContentButton');
            hideContentButton = document.getElementById(/*[[${headers.hideContentButton}]]*/ 'hideContentButton');
            someContent = document.getElementById(/*[[${body}]]*/ 'someContent');

            showContentButton.style.display = 'none';
            hideContentButton.style.display = 'block';
            someContent.style.display = 'block';
        }
    </script>
</span>