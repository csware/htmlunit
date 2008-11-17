/*
 * Copyright (c) 2002-2008 Gargoyle Software Inc.
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
package com.gargoylesoftware.htmlunit.javascript.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.html.HtmlBlockQuote;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlHeading1;
import com.gargoylesoftware.htmlunit.html.HtmlHeading2;
import com.gargoylesoftware.htmlunit.html.HtmlHeading3;
import com.gargoylesoftware.htmlunit.html.HtmlHeading4;
import com.gargoylesoftware.htmlunit.html.HtmlHeading5;
import com.gargoylesoftware.htmlunit.html.HtmlHeading6;
import com.gargoylesoftware.htmlunit.html.HtmlInlineQuotation;
import com.gargoylesoftware.htmlunit.html.HtmlTableBody;
import com.gargoylesoftware.htmlunit.html.HtmlTableFooter;
import com.gargoylesoftware.htmlunit.html.HtmlTableHeader;
import com.gargoylesoftware.htmlunit.javascript.SimpleScriptable;
import com.gargoylesoftware.htmlunit.javascript.StrictErrorHandler;
import com.gargoylesoftware.htmlunit.javascript.host.HTMLHeadingElement;
import com.gargoylesoftware.htmlunit.javascript.host.HTMLQuoteElement;
import com.gargoylesoftware.htmlunit.javascript.host.HTMLTableSectionElement;

/**
 * A container for all the JavaScript configuration information.
 * TODO - Need to add the logic to support the browser and JavaScript conditionals in the Class elements.
 *
 * @version $Revision$
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author Chris Erskine
 * @author Ahmed Ashour
 */
public final class JavaScriptConfiguration {
    private static Document XmlDocument_;

    /** Constant indicating that this function/property is used by the specified browser version. */
    public static final int ENABLED   = 1;

    /** Constant indicating that this function/property is not used by the specified browser version. */
    public static final int DISABLED  = 2;

    /** Constant indicating that this function/property is not defined in the configuration file. */
    public static final int NOT_FOUND = 3;

    private static Map<BrowserVersion, JavaScriptConfiguration> ConfigurationMap_ =
        new HashMap<BrowserVersion, JavaScriptConfiguration>(11);
    private static Map<String, String> ClassnameMap_ = new HashMap<String, String>();
    private static Map<Class < ? extends HtmlElement>, Class < ? extends SimpleScriptable>> HtmlJavaScriptMap_;

    private final Map<String, ClassConfiguration> configuration_;
    private final BrowserVersion browser_;

    /**
     * Constructor is only called from {@link #getInstance(BrowserVersion)} which is synchronized.
     * @param browser the browser version to use
     */
    private JavaScriptConfiguration(final BrowserVersion browser) {
        browser_ = browser;
        if (XmlDocument_ == null) {
            loadConfiguration();
        }

        if (XmlDocument_ == null) {
            throw new IllegalStateException("Configuration was not initialized - see log for details");
        }
        configuration_ = buildUsageMap();
    }

    /**
     * Test for a configuration having been loaded for testing.
     *
     * @return boolean - true if the XmlDocument has been loaded;
     */
    protected static boolean isDocumentLoaded() {
        return XmlDocument_ != null;
    }

    /**
     * Reset the this class to it's initial state. This method is
     * used for testing only.
     *
     */
    protected static void resetClassForTesting() {
        XmlDocument_ = null;
        ConfigurationMap_ = new HashMap<BrowserVersion, JavaScriptConfiguration>(11);
    }

    /**
     * Sets the document configuration for testing.
     * @param document - The configuration document
     */
    protected static void setXmlDocument(final Document document) {
        XmlDocument_ = document;
    }

    /**
     * Gets the configuration file and make it an input reader and then pass to the method to read the file.
     */
    protected static void loadConfiguration() {
        try {
            final Reader reader = getConfigurationFileAsReader();
            if (reader == null) {
                getLog().error("Unable to load JavaScriptConfiguration.xml");
            }
            else {
                loadConfiguration(reader);
                reader.close();
            }
        }
        catch (final Exception e) {
            getLog().error("Error when loading JavascriptConfiguration.xml", e);
            e.printStackTrace();
        }
    }

    /**
     * Loads the configuration from a supplied Reader.
     *
     * @param configurationReader - A reader pointing to the configuration
     */
    protected static void loadConfiguration(final Reader configurationReader) {
        final InputSource inputSource = new InputSource(configurationReader);

        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);

            final DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setErrorHandler(new StrictErrorHandler());

            XmlDocument_ = documentBuilder.parse(inputSource);
        }
        catch (final SAXParseException parseException) {
            getLog().error("line=[" + parseException.getLineNumber()
                    + "] columnNumber=[" + parseException.getColumnNumber()
                    + "] systemId=[" + parseException.getSystemId()
                    + "] publicId=[" + parseException.getPublicId() + "]", parseException);
        }
        catch (final Exception e) {
            getLog().error("Error when loading JavascriptConfiguration.xml", e);
        }
    }

    /**
     * Returns the instance that represents the configuration for the specified {@link BrowserVersion}.
     * This method is synchronized to allow multithreaded access to the JavaScript configuration.
     * @param browserVersion the {@link BrowserVersion}
     * @return the instance for the specified {@link BrowserVersion}
     */
    public static synchronized JavaScriptConfiguration getInstance(final BrowserVersion browserVersion) {
        if (browserVersion == null) {
            throw new IllegalStateException("BrowserVersion must be defined");
        }
        JavaScriptConfiguration configuration = ConfigurationMap_.get(browserVersion);

        if (configuration == null) {
            configuration = new JavaScriptConfiguration(browserVersion);
            ConfigurationMap_.put(browserVersion, configuration);
        }
        return configuration;
    }

    /**
     * Returns the configuration that has all entries. No constraints are put on the returned entries.
     *
     * @return the instance containing all entries from the configuration file
     */
    static JavaScriptConfiguration getAllEntries() {
        final JavaScriptConfiguration configuration = new JavaScriptConfiguration(null);
        return configuration;
    }

    private static Log getLog() {
        return LogFactory.getLog(JavaScriptConfiguration.class);
    }

    private static Reader getConfigurationFileAsReader() {
        final Class< ? > clazz = JavaScriptConfiguration.class;
        final String name = clazz.getPackage().getName().replace('.', '/') + '/' + "JavaScriptConfiguration.xml";
        InputStream inputStream = clazz.getClassLoader().getResourceAsStream(name);
        if (inputStream == null) {
            try {
                final String localizedName = name.replace('/', File.separatorChar);
                inputStream = new FileInputStream(localizedName);
            }
            catch (final IOException e) {
                // Fall through
            }
        }

        // If we are running maven tests then the path will be slightly different
        if (inputStream == null) {
            try {
                final String localizedName = ("./src/java" + name).replace('/', File.separatorChar);
                inputStream = new FileInputStream(localizedName);
            }
            catch (final IOException e) {
                // Fall through
            }
        }
        return new InputStreamReader(inputStream);
    }

    /**
     * Gets the set of keys for the class configurations.
     * @return the set of keys for the class configurations
     */
    public Set<String> keySet() {
        return configuration_.keySet();
    }

    private Map<String, ClassConfiguration> buildUsageMap() {
        final Map<String, ClassConfiguration> classMap = new HashMap<String, ClassConfiguration>(30);
        Node node = XmlDocument_.getDocumentElement().getFirstChild();
        while (node != null) {
            if (node instanceof Element) {
                final Element element = (Element) node;
                if (element.getTagName().equals("class")) {
                    final String className = element.getAttribute("name");
                    if (!testToExcludeElement(element)) {
                        try {
                            final ClassConfiguration classConfiguration = parseClassElement(className, element);
                            if (classConfiguration != null) {
                                classMap.put(className, classConfiguration);
                            }
                        }
                        catch (final ClassNotFoundException e) {
                            throw new IllegalStateException("The class was not found for '" + className + "'");
                        }
                    }
                }
            }
            node = node.getNextSibling();
        }
        return Collections.unmodifiableMap(classMap);
    }

    /**
     * Parses the class element to build the class configuration.
     * @param className the name of the class element
     * @param element the element to parse
     * @return the class element to build the class configuration
     * @throws ClassNotFoundException if the specified class could not be found
     */
    private ClassConfiguration parseClassElement(final String className, final Element element)
        throws ClassNotFoundException {
        final String notImplemented = element.getAttribute("notImplemented");
        if ("true".equalsIgnoreCase(notImplemented)) {
            return null;
        }
        final String linkedClassname = element.getAttribute("classname");
        final String jsConstructor = element.getAttribute("jsConstructor");
        final String superclassName = element.getAttribute("extends");
        final String htmlClassname = element.getAttribute("htmlClass");
        boolean jsObjectFlag = false;
        final String jsObjectStr = element.getAttribute("JSObject");
        if ("true".equalsIgnoreCase(jsObjectStr)) {
            jsObjectFlag = true;
        }
        final ClassConfiguration classConfiguration =
            new ClassConfiguration(className, linkedClassname, jsConstructor,
                    superclassName, htmlClassname, jsObjectFlag);
        ClassnameMap_.put(linkedClassname, className);
        Node node = element.getFirstChild();
        while (node != null) {
            if (node instanceof Element) {
                final Element childElement = (Element) node;
                final String tagName = childElement.getTagName();
                if (tagName.equals("property")) {
                    parsePropertyElement(classConfiguration, childElement);
                }
                else if (tagName.equals("function")) {
                    parseFunctionElement(classConfiguration, childElement);
                }
                else if (tagName.equals("constant")) {
                    parseConstantElement(classConfiguration, childElement);
                }
                else if (tagName.equals("javascript")) {
                    getLog().debug("javascript tag not yet handled for class " + linkedClassname);
                }
                else if (tagName.equals("browser")) {
                    getLog().debug("browser tag not yet handled for class " + linkedClassname);
                }
                else if (tagName.equals("doclink")) {
                    // ignore this link
                }
                else {
                    throw new IllegalStateException("Do not understand element type '"
                        + tagName + "' in '" + linkedClassname + "'");
                }
            }
            node = node.getNextSibling();
        }
        return classConfiguration;
    }

    /**
     * Parse out the values for the property.
     *
     * @param classConfiguration the configuration that is being built
     * @param element the property element
     */
    private void parsePropertyElement(final ClassConfiguration classConfiguration, final Element element) {
        final String notImplemented = element.getAttribute("notImplemented");
        if ("true".equalsIgnoreCase(notImplemented)) {
            return;
        }
        if (testToExcludeElement(element)) {
            return;
        }
        final String propertyName = element.getAttribute("name");
        boolean readable = false;
        boolean writeable = false;
        final String readFlag = element.getAttribute("readable");
        if ("true".equalsIgnoreCase(readFlag)) {
            readable = true;
        }
        final String writeFlag = element.getAttribute("writable");
        if ("true".equalsIgnoreCase(writeFlag)) {
            writeable = true;
        }
        classConfiguration.addProperty(propertyName, readable, writeable);
    }

    /**
     * Parses out the values from the function element.
     *
     * @param classConfiguration the configuration that is being built
     * @param element the function element
     */
    private void parseFunctionElement(final ClassConfiguration classConfiguration, final Element element) {
        final String notImplemented = element.getAttribute("notImplemented");
        if ("true".equalsIgnoreCase(notImplemented)) {
            return;
        }
        final String propertyName = element.getAttribute("name");
        if (testToExcludeElement(element)) {
            return;
        }
        classConfiguration.addFunction(propertyName);
    }

    /**
     * Parses out the values for the constant.
     *
     * @param classConfiguration the configuration that is being built
     * @param element the property element
     */
    private void parseConstantElement(final ClassConfiguration classConfiguration, final Element element) {
        if (testToExcludeElement(element)) {
            return;
        }
        final String constantName = element.getAttribute("name");
        classConfiguration.addConstant(constantName);
    }

    /**
     * Test for the browser and JavaScript constraints. Returns true if any constraints are present
     * and the browser does not meet the constraints.
     * @param element the element to scan the children of
     * @return true to exclude this element
     */
    private boolean testToExcludeElement(final Element element) {
        if (browser_ == null) {
            return false;
        }
        Node node = element.getFirstChild();
        boolean browserConstraint = false;
        boolean allowBrowser = false;
        boolean javascriptConstraint = false;
        boolean allowJavascriptConstraint = false;
        while (node != null) {
            if (node instanceof Element) {
                final Element childElement = (Element) node;
                if (childElement.getTagName().equals("browser")) {
                    browserConstraint = true;
                    if (testToIncludeForBrowserConstraint(childElement, browser_)) {
                        allowBrowser = true;
                    }
                }
                else if (childElement.getTagName().equals("javascript")) {
                    javascriptConstraint = true;
                    if (testToIncludeForJSConstraint(childElement, browser_)) {
                        allowJavascriptConstraint = true;
                    }
                }
            }
            node = node.getNextSibling();
        }
        if (browserConstraint && !allowBrowser) {
            return true;
        }
        if (javascriptConstraint && !allowJavascriptConstraint) {
            return true;
        }
        return false;
    }

    /**
     * Test to see if the supplied configuration matches for the parsed configuration for the named class
     * This is a method for testing.
     *
     * @param classname - the parsed classname to test
     * @param config - the expected configuration
     * @return true if they match
     */
    protected boolean classConfigEquals(final String classname, final ClassConfiguration config) {
        final ClassConfiguration myConfig = configuration_.get(classname);
        return config.equals(myConfig);
    }

    /**
     * @return the browser
     */
    public BrowserVersion getBrowser() {
        return browser_;
    }

    /**
     * Gets the class configuration for the supplied JavaScript class name.
     * @param classname the js class name
     * @return the class configuration for the supplied JavaScript class name
     */
    public ClassConfiguration getClassConfiguration(final String classname) {
        return configuration_.get(classname);
    }

    private boolean testToIncludeForBrowserConstraint(final Element element, final BrowserVersion browser) {
        if (!browser.getApplicationName().equals(element.getAttribute("name"))
            && (!browser.isFirefox() || !"Firefox".equals(element.getAttribute("name")))) {
            return false;
        }
        final String max = element.getAttribute("max-version");
        float maxVersion;
        if (max.length() == 0) {
            maxVersion = 0;
        }
        else {
            maxVersion = Float.parseFloat(max);
        }
        if ((maxVersion > 0) && (browser.getBrowserVersionNumeric() > maxVersion)) {
            return false;
        }

        float minVersion;
        final String min = element.getAttribute("min-version");
        if (min.length() == 0) {
            minVersion = 0;
        }
        else {
            minVersion = Float.parseFloat(min);
        }
        if ((minVersion > 0) && (browser.getBrowserVersionNumeric() < minVersion)) {
            return false;
        }
        return true;
    }

    private boolean testToIncludeForJSConstraint(final Element element, final BrowserVersion browser) {
        final String max = element.getAttribute("max-version");
        float maxVersion;
        if (max.length() == 0) {
            maxVersion = 0;
        }
        else {
            maxVersion = Float.parseFloat(max);
        }
        if ((maxVersion > 0) && (browser.getJavaScriptVersionNumeric() > maxVersion)) {
            return false;
        }

        float minVersion;
        final String min = element.getAttribute("min-version");
        if (min.length() == 0) {
            minVersion = 0;
        }
        else {
            minVersion = Float.parseFloat(min);
        }
        if ((minVersion > 0) && (browser.getJavaScriptVersionNumeric() < minVersion)) {
            return false;
        }
        return true;
    }

    /**
     * Returns the class for the given class name.
     * @param classname the classname that you want the implementing class for  (for testing only)
     * @return the class for the given class name
     */
    protected Class< ? > getClassObject(final String classname) {
        final ClassConfiguration config = configuration_.get(classname);
        return config.getLinkedClass();
    }

    /**
     * Gets the method that implements the getter for the given property based upon the class object.
     * @param clazz the actual class to use as reference
     * @param propertyName the property to find the getter for
     * @return the method that implements the getter for the given property based upon the class object
     */
    public Method getPropertyReadMethod(final Class< ? > clazz, final String propertyName) {
        final String classname = getClassnameForClass(clazz);
        return getPropertyReadMethod(classname, propertyName);
    }

    /**
     * Returns the method that implements the get function for in the class for the given class.
     *
     * @param classname the name of the class to work with
     * @param propertyName the property to find the getter for
     * @return the method that implements the get function for in the class for the given class
     */
    public Method getPropertyReadMethod(String classname, final String propertyName) {
        ClassConfiguration config;
        Method theMethod;
        while (classname.length() > 0) {
            config = configuration_.get(classname);
            if (config == null) {
                return null;
            }
            theMethod = config.getPropertyReadMethod(propertyName);
            if (theMethod != null) {
                return theMethod;
            }
            classname = config.getExtendedClass();
        }
        return null;
    }

    private ClassConfiguration.PropertyInfo findPropertyInChain(final String classname, final String propertyName) {
        String workname = classname;
        ClassConfiguration config;
        while (workname.length() > 0) {
            config = configuration_.get(workname);
            final ClassConfiguration.PropertyInfo info = config.getPropertyInfo(propertyName);
            if (info != null) {
                return info;
            }
            workname = config.getExtendedClass();
        }
        return null;
    }

    /**
     * Gets the method that implements the setter for the given property based upon the class object.
     * @param clazz the actual class to use as reference
     * @param propertyName the property to find the getter for
     * @return the method that implements the setter for the given property based upon the class object
     */
    public Method getPropertyWriteMethod(final Class< ? > clazz, final String propertyName) {
        final String classname = getClassnameForClass(clazz);
        return getPropertyWriteMethod(classname, propertyName);
    }

    /**
     * Returns the method that implements the set function in the class for the given class.
     *
     * @param classname the name of the class to work with
     * @param propertyName the property to find the setter for
     * @return the method that implements the set function in the class for the given class
     */
    public Method getPropertyWriteMethod(String classname, final String propertyName) {
        ClassConfiguration config;
        Method theMethod;
        while (classname.length() > 0) {
            config = configuration_.get(classname);
            theMethod = config.getPropertyWriteMethod(propertyName);
            if (theMethod != null) {
                return theMethod;
            }
            classname = config.getExtendedClass();
        }
        return null;
    }

    /**
     * Gets the method that implements the setter for the given property based upon the class object.
     *
     * @param clazz the actual class to use as reference
     * @param functionName the function to find the method for
     * @return the method that implements the setter for the given property based upon the class object
     */
    public Method getFunctionMethod(final Class< ? > clazz, final String functionName) {
        final String classname = getClassnameForClass(clazz);
        return getFunctionMethod(classname, functionName);
    }

    /**
     * Returns the method that implements the given function in the class for the given class.
     *
     * @param classname the name of the class to work with
     * @param functionName the function to find the method for
     * @return the method that implements the given function in the class for the given class
     */
    public Method getFunctionMethod(String classname, final String functionName) {
        ClassConfiguration config;
        Method theMethod;
        while (classname.length() > 0) {
            config = configuration_.get(classname);
            theMethod = config.getFunctionMethod(functionName);
            if (theMethod != null) {
                return theMethod;
            }
            classname = config.getExtendedClass();
        }
        return null;
    }

    /**
     * Checks to see if there is an entry for the given property.
     *
     * @param clazz the class the property is for
     * @param propertyName the name of the property
     * @return boolean <tt>true</tt> if the property exists
     */
    public boolean propertyExists(final Class< ? > clazz, final String propertyName) {
        final String classname = getClassnameForClass(clazz);
        return propertyExists(classname, propertyName);
    }

    /**
     * Checks to see if there is an entry for the given property.
     *
     * @param classname the class the property is for
     * @param propertyName the name of the property
     * @return boolean <tt>true</tt> if the property exists
     */
    public boolean propertyExists(final String classname, final String propertyName) {
        final ClassConfiguration.PropertyInfo info = findPropertyInChain(classname, propertyName);
        if (info == null) {
            return false;
        }
        return true;
    }

    /**
     * Returns the classname that the given class implements. If the class is
     * the input class, then the name is extracted from the type that the Input class
     * is masquerading as.
     * FIXME - Implement the Input class processing
     * @param clazz
     * @return the classname
     */
    private String getClassnameForClass(final Class< ? > clazz) {
        final String name = ClassnameMap_.get(clazz.getName());
        if (name == null) {
            throw new IllegalStateException("Did not find the mapping of the class to the classname for "
                + clazz.getName());
        }
        return name;
    }

    /**
     * Returns an immutable map containing the HTML to JavaScript mappings. Keys are
     * java classes for the various HTML classes (ie HtmlInput.class) and the values
     * are the JavaScript class names (ie "HTMLAnchorElement").
     * @return the mappings
     */
    @SuppressWarnings("unchecked")
    public static synchronized Map<Class < ? extends HtmlElement>, Class < ? extends SimpleScriptable>>
    getHtmlJavaScriptMapping() {
        if (HtmlJavaScriptMap_ != null) {
            return HtmlJavaScriptMap_;
        }
        final JavaScriptConfiguration configuration = JavaScriptConfiguration.getAllEntries();

        final Map<Class < ? extends HtmlElement>, Class < ? extends SimpleScriptable>> map =
            new HashMap<Class < ? extends HtmlElement>, Class < ? extends SimpleScriptable>>();

        for (String jsClassname : configuration.keySet()) {
            ClassConfiguration classConfig = configuration.getClassConfiguration(jsClassname);
            final String htmlClassname = classConfig.getHtmlClassname();
            if (htmlClassname != null) {
                try {
                    final Class< ? extends HtmlElement> htmlClass =
                        (Class< ? extends HtmlElement>) Class.forName(htmlClassname);
                    // preload and validate that the class exists
                    getLog().debug("Mapping " + htmlClass.getName() + " to " + jsClassname);
                    while (!classConfig.isJsObject()) {
                        jsClassname = classConfig.getExtendedClass();
                        classConfig = configuration.getClassConfiguration(jsClassname);
                    }
                    map.put(htmlClass, classConfig.getLinkedClass());
                }
                catch (final ClassNotFoundException e) {
                    throw new NoClassDefFoundError(e.getMessage());
                }
            }
        }
        map.put(HtmlHeading1.class, HTMLHeadingElement.class);
        map.put(HtmlHeading2.class, HTMLHeadingElement.class);
        map.put(HtmlHeading3.class, HTMLHeadingElement.class);
        map.put(HtmlHeading4.class, HTMLHeadingElement.class);
        map.put(HtmlHeading5.class, HTMLHeadingElement.class);
        map.put(HtmlHeading6.class, HTMLHeadingElement.class);

        map.put(HtmlInlineQuotation.class, HTMLQuoteElement.class);
        map.put(HtmlBlockQuote.class, HTMLQuoteElement.class);

        map.put(HtmlTableBody.class, HTMLTableSectionElement.class);
        map.put(HtmlTableHeader.class, HTMLTableSectionElement.class);
        map.put(HtmlTableFooter.class, HTMLTableSectionElement.class);

        HtmlJavaScriptMap_ = Collections.unmodifiableMap(map);
        return HtmlJavaScriptMap_;
    }
}
