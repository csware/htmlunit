/*
 * Copyright (c) 2002-2010 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.html;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.gargoylesoftware.htmlunit.BrowserRunner;
import com.gargoylesoftware.htmlunit.WebTestCase;
import com.gargoylesoftware.htmlunit.BrowserRunner.Alerts;

/**
 * Tests for {@link HtmlBase}.
 *
 * @version $Revision$
 * @author Ahmed Ashour
 */
@RunWith(BrowserRunner.class)
public class HtmlBaseTest extends WebTestCase {

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(FF = "[object HTMLBaseElement]", IE = "[object]")
    public void simpleScriptable() throws Exception {
        final String html = "<html><head>\n"
            + "<base id='myId' target='MyNewWindow'>\n"
            + "<script>\n"
            + "  function test() {\n"
            + "    alert(document.getElementById('myId'));\n"
            + "  }\n"
            + "</script>\n"
            + "</head><body onload='test()'>\n"
            + "</body></html>";

        final HtmlPage page = loadPageWithAlerts(html);
        assertTrue(HtmlBase.class.isInstance(page.getHtmlElementById("myId")));
    }
}
