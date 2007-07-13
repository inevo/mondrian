/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2005-2007 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/

package mondrian.tui;

import mondrian.spi.CatalogLocator;
import mondrian.spi.impl.CatalogLocatorImpl;
import mondrian.xmla.DataSourcesConfig;
import mondrian.olap.Util;
import mondrian.olap.Role;
import mondrian.xmla.XmlaConstants;
import mondrian.xmla.XmlaHandler;
import mondrian.xmla.XmlaRequest;
import mondrian.xmla.XmlaResponse;
import mondrian.xmla.XmlaServlet;
import mondrian.xmla.impl.DefaultXmlaServlet;
import mondrian.xmla.impl.DefaultXmlaRequest;
import mondrian.xmla.impl.DefaultXmlaResponse;
import mondrian.rolap.RolapConnectionProperties;
import org.eigenbase.xom.DOMWrapper;
import org.eigenbase.xom.Parser;
import org.eigenbase.xom.XOMUtil;
import org.eigenbase.xom.XOMException;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;

/**
 * This files provide support for making XMLA requests and looking at
 * the responses.
 *
 * @author Richard M. Emberson
 * @version $Id$
 */
public class XmlaSupport {
    private static final Logger LOGGER = Logger.getLogger(XmlaSupport.class);

    public static final String nl = Util.nl;
    public static final String SOAP_PREFIX = XmlaConstants.SOAP_PREFIX;

    public static final String CATALOG_NAME = "FoodMart";
    public static final String DATASOURCE_NAME = "MondrianFoodMart";
    public static final String DATASOURCE_DESCRIPTION =
            "Mondrian FoodMart data source";
    public static final String DATASOURCE_INFO =
            "Provider=Mondrian;DataSource=MondrianFoodMart;";

    public static final Map<String, String> ENV;

    // Setup the Map used to instantiate XMLA template documents.
    // Have to see if we need to be able to dynamically change these values.
    static {
        ENV = new HashMap<String, String>();
        ENV.put("catalog", CATALOG_NAME);
        ENV.put("datasource", DATASOURCE_INFO);
    }

    /**
     * This is a parameterized XSLT.
     * The parameters are:
     *   "soap" with values "none" or empty
     *   "content" with values "schemadata", "schema", "data" or empty
     * With these setting one can extract from an XMLA SOAP message
     * the soap wrapper plus body or simply the body; the complete
     * body (schema and data), only the schema of the body, only the
     * data of the body or none of the body
     *
     */
    public static String getXmlaTransform(String xmlaPrefix) {
        return
        "<?xml version='1.0'?>" +
        "<xsl:stylesheet " +
        "  xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
        "  xmlns:xalan='http://xml.apache.org/xslt'" +
        "  xmlns:xsd='http://www.w3.org/2001/XMLSchema'" +
        "  xmlns:ROW='urn:schemas-microsoft-com:xml-analysis:rowset'" +
        "  xmlns:SOAP-ENV='http://schemas.xmlsoap.org/soap/envelope/' " +
        "  xmlns:" + xmlaPrefix + "='urn:schemas-microsoft-com:xml-analysis'" +
        "  version='1.0'" +
        ">" +
        "<xsl:output method='xml' " +
        "  encoding='UTF-8'" +
        "  indent='yes' " +
        "  xalan:indent-amount='2'/>" +
        "<xsl:param name='content'/>" +
        "<xsl:param name='soap'/>" +
        "<!-- consume '/' and apply -->" +
        "<xsl:template match='/'>" +
        "  <xsl:apply-templates/>" +
        "</xsl:template>" +
        "<!-- copy 'Envelope' unless soap==none --> " +
        "<xsl:template match='SOAP-ENV:Envelope'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$soap='none'\"> " +
        "      <xsl:apply-templates/> " +
        "    </xsl:when> " +
        "    <xsl:otherwise> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:otherwise>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'Header' unless soap==none --> " +
        "<xsl:template match='SOAP-ENV:Header'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$soap='none'\"> " +
        "      <xsl:apply-templates/> " +
        "    </xsl:when> " +
        "    <xsl:otherwise>  " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:otherwise>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'Body' unless soap==none --> " +
        "<xsl:template match='SOAP-ENV:Body'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$soap='none'\"> " +
        "      <xsl:apply-templates/> " +
        "    </xsl:when> " +
        "    <xsl:otherwise>  " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:otherwise>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'DiscoverResponse' unless soap==none --> " +
        "<xsl:template match='" + xmlaPrefix + ":DiscoverResponse'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$soap='none'\"> " +
        "      <xsl:apply-templates/> " +
        "    </xsl:when> " +
        "    <xsl:otherwise> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:otherwise>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'return' unless soap==none --> " +
        "<xsl:template match='" + xmlaPrefix + ":return'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$soap='none'\"> " +
        "      <xsl:apply-templates/> " +
        "    </xsl:when> " +
        "    <xsl:otherwise> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:otherwise>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'root' unless soap==none --> " +
        "<xsl:template match='ROW:root'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$soap='none'\"> " +
        "      <xsl:apply-templates/> " +
        "    </xsl:when> " +
        "    <xsl:otherwise> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:otherwise > " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'schema' if content==schema or schemadata --> " +
        "<xsl:template match='xsd:schema'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$content='schemadata'\"> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:when> " +
        "    <xsl:when test=\"$content='schema'\"> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:when> " +
        "  <xsl:otherwise/>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy 'row' if content==data or schemadata --> " +
        "<xsl:template match='ROW:row'> " +
        "  <xsl:choose> " +
        "    <xsl:when test=\"$content='schemadata'\"> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:when> " +
        "    <xsl:when test=\"$content='data'\"> " +
        "      <xsl:copy> " +
        "        <xsl:apply-templates select='@*|node()'/> " +
        "      </xsl:copy> " +
        "    </xsl:when> " +
        "    <xsl:otherwise/>  " +
        "  </xsl:choose> " +
        "</xsl:template> " +
        "<!-- copy everything else --> " +
        "<xsl:template match='*|@*'> " +
        "  <xsl:copy> " +
        "    <xsl:apply-templates select='@*|node()'/> " +
        "  </xsl:copy> " +
        "</xsl:template> " +
        "</xsl:stylesheet>";
    }

    /**
     * This is the prefix used in xpath and transforms for the xmla rowset
     * namespace "urn:schemas-microsoft-com:xml-analysis:rowset".
     */
    public static final String ROW_SET_PREFIX = "ROW";

    private static CatalogLocator CATALOG_LOCATOR = null;
    private static String soapFaultXPath = null;
    private static String soapHeaderAndBodyXPath = null;
    private static String soapBodyXPath = null;
    private static String soapXmlaRootXPath = null;
    private static String xmlaRootXPath = null;


    /////////////////////////////////////////////////////////////////////////
    // xmla help
    /////////////////////////////////////////////////////////////////////////
    public static CatalogLocator getCatalogLocator() {
        if (CATALOG_LOCATOR == null) {
            CATALOG_LOCATOR = new CatalogLocatorImpl();
        }
        return CATALOG_LOCATOR;
    }

    public static DataSourcesConfig.DataSources getDataSources(
        String connectString,
        Map<String, String> catalogNameUrls)
        throws XOMException
    {
        String str = getDataSourcesText(connectString, catalogNameUrls);
        StringReader dsConfigReader = new StringReader(str);

        final Parser xmlParser = XOMUtil.createDefaultParser();
        final DOMWrapper def = xmlParser.parse(dsConfigReader);

        DataSourcesConfig.DataSources datasources =
            new DataSourcesConfig.DataSources(def);

        return datasources;
    }

    /**
     * With a connection string, generate the DataSource xml. Since this
     * is used by directly, same process, communicating with XMLA
     * Mondrian, the fact that the URL contains "localhost" is not
     * important.
     *
     * @param connectString Connect string
     * @param catalogNameUrls array of catalog names, catalog url pairs
     */
    public static String getDataSourcesText(
            String connectString,
            Map<String, String> catalogNameUrls) {
        StringBuilder buf = new StringBuilder(500);
        buf.append("<?xml version=\"1.0\"?>");
        buf.append(nl);
        buf.append("<DataSources>");
        buf.append(nl);
        buf.append("   <DataSource>");
        buf.append(nl);
        buf.append("       <DataSourceName>");
        buf.append(DATASOURCE_NAME);
        buf.append("</DataSourceName>");
        buf.append(nl);
        buf.append("       <DataSourceDescription>");
        buf.append(DATASOURCE_DESCRIPTION);
        buf.append("</DataSourceDescription>");
        buf.append(nl);
        buf.append("       <URL>http://localhost:8080/mondrian/xmla</URL>");
        buf.append(nl);
        buf.append("       <DataSourceInfo><![CDATA[");
        buf.append(connectString);
        buf.append("]]></DataSourceInfo>");
        buf.append(nl);

        buf.append("       <ProviderName>Mondrian</ProviderName>");
        buf.append(nl);
        buf.append("       <ProviderType>MDP</ProviderType>");
        buf.append(nl);
        buf.append("       <AuthenticationMode>Unauthenticated</AuthenticationMode>");
        buf.append(nl);
        buf.append("       <Catalogs>");
        buf.append(nl);
        for (Map.Entry<String,String> catalogNameUrl : catalogNameUrls.entrySet()) {
            String name = catalogNameUrl.getKey();
            String url = catalogNameUrl.getValue();
            buf.append("           <Catalog name='");
            buf.append(name);
            buf.append("'><Definition>");
            buf.append(url);
            buf.append("</Definition></Catalog>");
        }
        buf.append("       </Catalogs>");
        buf.append(nl);
        buf.append("   </DataSource>");
        buf.append(nl);
        buf.append("</DataSources>");
        buf.append(nl);
        String datasources = buf.toString();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("XmlaSupport.getDataSources: datasources="+
                    datasources);
        }
        return datasources;
    }
    public static String getSoapFaultXPath() {
        if (XmlaSupport.soapFaultXPath == null) {
            StringBuilder buf = new StringBuilder(100);
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Envelope");
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Body");
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Fault");
            buf.append("/*");
            String xpath = buf.toString();
            XmlaSupport.soapFaultXPath = xpath;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "XmlaSupport.getSoapFaultXPath: xpath="+ xpath);
            }
        }
        return XmlaSupport.soapFaultXPath;
    }

    public static String getSoapHeaderAndBodyXPath() {
        if (XmlaSupport.soapHeaderAndBodyXPath == null) {
            StringBuilder buf = new StringBuilder(100);
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Envelope");
            buf.append("/*");
            String xpath = buf.toString();
            XmlaSupport.soapHeaderAndBodyXPath = xpath;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "XmlaSupport.getSoapHeaderAndBodyXPath: xpath="+ xpath);
            }
        }
        return XmlaSupport.soapHeaderAndBodyXPath;
    }
    public static String getSoapBodyXPath() {
        if (XmlaSupport.soapBodyXPath == null) {
            StringBuilder buf = new StringBuilder(100);
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Envelope");
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Body");
            buf.append("/*");
            String xpath = buf.toString();
            XmlaSupport.soapBodyXPath = xpath;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("XmlaSupport.getSoapBodyXPath: xpath="+ xpath);
            }
        }
        return XmlaSupport.soapBodyXPath;
    }

    public static String getSoapXmlaRootXPath(String xmlaPrefix) {
        if (XmlaSupport.soapXmlaRootXPath == null) {
            StringBuilder buf = new StringBuilder(20);
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Envelope");
            buf.append('/');
            buf.append(SOAP_PREFIX);
            buf.append(":Body");
            buf.append("/").append(xmlaPrefix).append(":DiscoverResponse");
            buf.append("/").append(xmlaPrefix).append(":return");
            buf.append('/');
            buf.append(ROW_SET_PREFIX);
            buf.append(":root");
            buf.append("/*");
            String xpath = buf.toString();
            XmlaSupport.soapXmlaRootXPath = xpath;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("XmlaSupport.getSoapXmlaRootXPath: xpath="+ xpath);
            }
        }
        return XmlaSupport.soapXmlaRootXPath;
    }

    public static String getXmlaRootXPath(String xmlaPrefix) {
        if (XmlaSupport.xmlaRootXPath == null) {
            StringBuilder buf = new StringBuilder(20);
            buf.append("/").append(xmlaPrefix).append(":DiscoverResponse");
            buf.append("/").append(xmlaPrefix).append(":return");
            buf.append('/');
            buf.append(ROW_SET_PREFIX);
            buf.append(":root");
            buf.append("/*");
            String xpath = buf.toString();
            XmlaSupport.xmlaRootXPath = xpath;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("XmlaSupport.getXmlaRootXPath: xpath="+ xpath);
            }
        }
        return XmlaSupport.xmlaRootXPath;
    }



    public static Node[] extractNodesFromSoapXmla(byte[] bytes)
            throws SAXException, IOException {
        Document doc = XmlUtil.parse(bytes);
        return extractNodesFromSoapXmla(doc);
    }

    public static Node[] extractNodesFromSoapXmla(Document doc)
            throws SAXException, IOException {

        final String xmlaPrefix = "xmla";
        String xpath = getSoapXmlaRootXPath(xmlaPrefix);

        // Note that this is SOAP 1.1 version uri
        String[][] nsArray = new String[][] {
             { SOAP_PREFIX, XmlaConstants.NS_SOAP_ENV_1_1 },
             { xmlaPrefix, XmlaConstants.NS_XMLA },
             { ROW_SET_PREFIX, XmlaConstants.NS_XMLA_ROWSET }
        };

        return extractNodes(doc, xpath, nsArray);

    }

    public static Node[] extractNodesFromXmla(byte[] bytes)
            throws SAXException, IOException {
        Document doc = XmlUtil.parse(bytes);
        return extractNodesFromXmla(doc);
    }

    public static Node[] extractNodesFromXmla(Document doc)
            throws SAXException, IOException {

        final String xmlaPrefix = "xmla";
        String xpath = getXmlaRootXPath(xmlaPrefix);

        String[][] nsArray = new String[][] {
             {xmlaPrefix, XmlaConstants.NS_XMLA },
             { ROW_SET_PREFIX, XmlaConstants.NS_XMLA_ROWSET }
        };

        return extractNodes(doc, xpath, nsArray);
    }

    public static Node[] extractFaultNodesFromSoap(byte[] bytes)
            throws SAXException, IOException {
        Document doc = XmlUtil.parse(bytes);
        return extractFaultNodesFromSoap(doc);
    }
    public static Node[] extractFaultNodesFromSoap(Document doc)
            throws SAXException, IOException {
        String xpath = getSoapFaultXPath();

        String[][] nsArray = new String[][] {
             { SOAP_PREFIX, XmlaConstants.NS_SOAP_ENV_1_1 },
        };

        Node[] nodes = extractNodes(doc, xpath, nsArray);
        return nodes;
    }

    public static Node[] extractHeaderAndBodyFromSoap(byte[] bytes)
            throws SAXException, IOException {
        Document doc = XmlUtil.parse(bytes);
        return extractHeaderAndBodyFromSoap(doc);
    }
    public static Node[] extractHeaderAndBodyFromSoap(Document doc)
            throws SAXException, IOException {
        String xpath = getSoapHeaderAndBodyXPath();

        String[][] nsArray = new String[][] {
             { SOAP_PREFIX, XmlaConstants.NS_SOAP_ENV_1_1 },
        };

        Node[] nodes = extractNodes(doc, xpath, nsArray);
        return nodes;
    }
    public static Document extractBodyFromSoap(Document doc)
            throws SAXException, IOException {
        String xpath = getSoapBodyXPath();

        String[][] nsArray = new String[][] {
             { SOAP_PREFIX, XmlaConstants.NS_SOAP_ENV_1_1 },
        };

        Node[] nodes = extractNodes(doc, xpath, nsArray);
        return (nodes.length == 1)
            ? XmlUtil.newDocument(nodes[0], true) : null;
    }

    /**
     * Given a Document and an xpath/namespace-array pair, extract and return
     * the Nodes resulting from applying the xpath.
     *
     * @throws SAXException
     * @throws IOException
     */
    public static Node[] extractNodes(
            Node node, String xpath, String[][] nsArray)
            throws SAXException, IOException {

        Document contextDoc = XmlUtil.createContextDocument(nsArray);
        Node[] nodes = XmlUtil.selectAsNodes(node, xpath, contextDoc);

        if (LOGGER.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder(1024);
            buf.append("XmlaSupport.extractNodes: ");
            buf.append("nodes.length=");
            buf.append(nodes.length);
            buf.append(nl);
            for (Node n : nodes) {
                String str = XmlUtil.toString(n, false);
                buf.append(str);
                buf.append(nl);
            }
            LOGGER.debug(buf.toString());
        }

        return nodes;
    }
    /////////////////////////////////////////////////////////////////////////
    // soap xmla file
    /////////////////////////////////////////////////////////////////////////
    /**
     * Process the given input file as a SOAP-XMLA request.
     *
     */
    public static byte[] processSoapXmla(
        File file,
        String connectString,
        Map<String, String> catalogNameUrls,
        String cbClassName)
        throws IOException, ServletException, SAXException
    {
        String requestText = XmlaSupport.readFile(file);
        return processSoapXmla(
            requestText, connectString, catalogNameUrls, cbClassName);
    }

    public static byte[] processSoapXmla(
        Document doc,
        String connectString,
        Map<String, String> catalogNameUrls,
        String cbClassName)
        throws IOException, ServletException, SAXException
    {
        String requestText = XmlUtil.toString(doc, false);
        return processSoapXmla(
            requestText, connectString, catalogNameUrls, cbClassName);
    }

    public static byte[] processSoapXmla(
        String requestText,
        String connectString,
        Map<String, String> catalogNameUrls,
        String cbClassName)
        throws IOException, ServletException, SAXException
    {
        // read soap file
        File dsFile = null;
        try {


            // Create datasource file and put datasource xml into it.
            // Mark it as delete on exit.
            String dataSourceText =
                XmlaSupport.getDataSourcesText(connectString, catalogNameUrls);

            dsFile = File.createTempFile("datasources.xml", null);

            OutputStream out = new FileOutputStream(dsFile);
            out.write(dataSourceText.getBytes());
            out.flush();

            // Glean the role for the request from the connect string. (Ideally,
            // we would do the reverse: read the role from an HTTP param in the
            // request, and modify the connect string accordingly.)
            Util.PropertyList propertyList =
                Util.parseConnectString(connectString);
            String roleName =
                propertyList.get(RolapConnectionProperties.Role.name());

            byte[] reqBytes = requestText.getBytes();
            // make request
            MockHttpServletRequest req = new MockHttpServletRequest(reqBytes);
            req.setMethod("POST");
            req.setContentType("text/xml");
            if (roleName != null) {
                req.setUserInRole(roleName, true);
            }

            // make response
            MockHttpServletResponse res = new MockHttpServletResponse();
            res.setCharacterEncoding("UTF-8");

            // process
            MockServletContext servletContext = new MockServletContext();
            MockServletConfig servletConfig = new MockServletConfig(servletContext);

            servletConfig.addInitParameter(XmlaServlet.PARAM_CALLBACKS, cbClassName);
            servletConfig.addInitParameter(
                XmlaServlet.PARAM_CHAR_ENCODING, "UTF-8");
            servletConfig.addInitParameter(
                XmlaServlet.PARAM_DATASOURCES_CONFIG, dsFile.toURL().toString());

            Servlet servlet = new DefaultXmlaServlet();
            servlet.init(servletConfig);
            servlet.service(req, res);

            return res.toByteArray();
        } finally {
            if (dsFile != null) {
                dsFile.delete();
            }
        }
    }

    public static Servlet makeServlet(
        String connectString,
        Map<String, String> catalogNameUrls,
        String cbClassName)
        throws IOException, ServletException, SAXException
    {
        // Create datasource file and put datasource xml into it.
        // Mark it as delete on exit.
        String dataSourceText =
            XmlaSupport.getDataSourcesText(connectString, catalogNameUrls);

        File dsFile = File.createTempFile("datasources.xml", null);

        //////////////////////////////////////////////////////////
        // NOTE: this is ok for CmdRunner or JUnit testing but
        // deleteOnExit is NOT good for production
        //////////////////////////////////////////////////////////
        dsFile.deleteOnExit();

        OutputStream out = new FileOutputStream(dsFile);
        out.write(dataSourceText.getBytes());
        out.flush();

        // process
        MockServletContext servletContext = new MockServletContext();
        MockServletConfig servletConfig = new MockServletConfig(servletContext);
        servletConfig.addInitParameter(XmlaServlet.PARAM_CALLBACKS, cbClassName);
        servletConfig.addInitParameter(
            XmlaServlet.PARAM_CHAR_ENCODING, "UTF-8");
        servletConfig.addInitParameter(
            XmlaServlet.PARAM_DATASOURCES_CONFIG, dsFile.toURL().toString());

        Servlet servlet = new DefaultXmlaServlet();
        servlet.init(servletConfig);

        return servlet;
    }
    public static byte[] processSoapXmla(File file, Servlet servlet)
            throws IOException, ServletException, SAXException {

        String requestText = XmlaSupport.readFile(file);
        return processSoapXmla(requestText, servlet);
    }
    public static byte[] processSoapXmla(Document doc, Servlet servlet)
            throws IOException, ServletException, SAXException {

        String requestText = XmlUtil.toString(doc, false);
        return processSoapXmla(requestText, servlet);
    }
    public static byte[] processSoapXmla(String requestText, Servlet servlet)
            throws IOException, ServletException, SAXException {

        byte[] reqBytes = requestText.getBytes();
        // make request
        MockHttpServletRequest req = new MockHttpServletRequest(reqBytes);
        req.setMethod("POST");
        req.setContentType("text/xml");

        // make response
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setCharacterEncoding("UTF-8");

        servlet.service(req, res);

        return res.toByteArray();
    }



    /**
     * Check is a byte array containing a SOAP-XMLA response method is valid.
     * Schema validation occurs if the XMLA response contains both a content
     * and schmema section. This includes both the SOAP elements and the
     * SOAP body content, the XMLA response.
     *
     */
    public static boolean validateSchemaSoapXmla(byte[] bytes)
            throws SAXException, IOException,
                ParserConfigurationException,
                TransformerException {

        return validateEmbeddedSchema(
            bytes,
            XmlUtil.getSoapXmlaXds2xs("xmla"),
            XmlUtil.getSoapXmlaXds2xd("xmla"));
    }


    /////////////////////////////////////////////////////////////////////////
    // xmla file
    /////////////////////////////////////////////////////////////////////////

    /**
     * Processes the given input file as an XMLA request (no SOAP elements).
     */
    public static byte[] processXmla(
        File file,
        String connectString,
        Map<String, String> catalogNameUrls)
        throws IOException, SAXException, XOMException
    {
        return processXmla(file, connectString, catalogNameUrls, null);
    }
    public static byte[] processXmla(
        File file,
        String connectString,
        Map<String, String> catalogNameUrls,
        Role role)
        throws IOException, SAXException, XOMException
    {
        String requestText = XmlaSupport.readFile(file);
        return processXmla(requestText, connectString, catalogNameUrls, role);
    }

    public static byte[] processXmla(
        String requestText,
        String connectString,
        Map<String, String> catalogNameUrls)
        throws IOException, SAXException, XOMException
    {
        return processXmla(requestText, connectString, catalogNameUrls, null);
    }
    public static byte[] processXmla(
        String requestText,
        String connectString,
        Map<String, String> catalogNameUrls,
        Role role)
        throws IOException, SAXException, XOMException
    {
        Document requestDoc = XmlUtil.parseString(requestText);
        return processXmla(requestDoc, connectString, catalogNameUrls, role);
    }

    public static byte[] processXmla(
        Document requestDoc,
        String connectString,
        Map<String, String> catalogNameUrls)
        throws IOException, XOMException
    {
        Element requestElem = requestDoc.getDocumentElement();
        return processXmla(requestElem, connectString, catalogNameUrls, null);
    }

    public static byte[] processXmla(
        Document requestDoc,
        String connectString,
        Map<String, String> catalogNameUrls,
        Role role)
        throws IOException, XOMException
    {
        Element requestElem = requestDoc.getDocumentElement();
        return processXmla(requestElem, connectString, catalogNameUrls, role);
    }
    public static byte[] processXmla(
        Element requestElem,
        String connectString,
        Map<String, String> catalogNameUrls)
        throws IOException, XOMException
    {
        return processXmla(requestElem, connectString, catalogNameUrls, null);
    }

    public static byte[] processXmla(
        Element requestElem,
        String connectString,
        Map<String, String> catalogNameUrls,
        Role role)
        throws IOException, XOMException
    {
        // make request
        CatalogLocator cl = getCatalogLocator();
        DataSourcesConfig.DataSources dataSources =
            getDataSources(connectString, catalogNameUrls);
        XmlaHandler handler = new XmlaHandler(dataSources, cl, "xmla");
        Util.PropertyList propertyList = Util.parseConnectString(connectString);
        String roleName =
            propertyList.get(RolapConnectionProperties.Role.name());

        XmlaRequest request = null;
        if (role != null) {
            request = new DefaultXmlaRequest(requestElem, role);
        } else if (roleName != null) {
            request = new DefaultXmlaRequest(requestElem, roleName);
        } else {
            request = new DefaultXmlaRequest(requestElem);
        }

        // make response
        ByteArrayOutputStream resBuf = new ByteArrayOutputStream();
        XmlaResponse response = new DefaultXmlaResponse(resBuf, "UTF-8");

        handler.process(request, response);

        return resBuf.toByteArray();
    }

    /**
     * Check is a byte array containing a XMLA response method is valid.
     * Schema validation occurs if the XMLA response contains both a content
     * and schmema section. This should not be used when the byte array
     * contains both the SOAP elements and content, but only for the content.
     *
     */
    public static boolean validateSchemaXmla(byte[] bytes)
            throws SAXException, IOException,
                ParserConfigurationException,
                TransformerException {

        return validateEmbeddedSchema(bytes,
            XmlUtil.getXmlaXds2xs("xmla"),
            XmlUtil.getXmlaXds2xd("xmla"));
    }

    /////////////////////////////////////////////////////////////////////////
    // helpers
    /////////////////////////////////////////////////////////////////////////

    /**
     * This validates a SOAP-XMLA response using xpaths to extract the
     * schema and data parts. In addition, it does a little surgery on
     * the DOMs removing the schema nodes from the XMLA root node.
     */
    public static boolean validateSoapXmlaUsingXpath(byte[] bytes)
            throws SAXException, IOException {

        if (! XmlUtil.supportsValidation()) {
            return false;
        }
        Node[] nodes = extractNodesFromSoapXmla(bytes);
        return validateNodes(nodes);
    }

    /**
     * This validates a XMLA response using xpaths to extract the
     * schema and data parts. In addition, it does a little surgery on
     * the DOMs removing the schema nodes from the XMLA root node.
     *
     */
    public static boolean validateXmlaUsingXpath(byte[] bytes)
            throws SAXException, IOException {

        if (! XmlUtil.supportsValidation()) {
            return false;
        }
        Node[] nodes = extractNodesFromXmla(bytes);
        return validateNodes(nodes);
    }

    /**
     * Validate Nodes with throws an error if validation was attempted but
     * failed, returns true if validation was successful and false if
     * validation was not tried.
     *
     * @return  true if validation succeeded, false if validation was not tried
     */
    public static boolean validateNodes(Node[] nodes)
            throws SAXException, IOException {

        if (! XmlUtil.supportsValidation()) {
            return false;
        }
        if (nodes.length == 0) {
            // no nodes
            return false;
        } else if (nodes.length == 1) {
            // only data or schema but not both
            return false;
        } else if (nodes.length > 2) {
            // TODO: error
            return false;
        }

        Node schemaNode = nodes[0];
        Node rowNode = nodes[1];

        // This is the "root" node that contains both the schemaNode and
        // the rowNode.
        Node rootNode = rowNode.getParentNode();
        // Remove the schemaNode from the root Node.
        rootNode.removeChild(schemaNode);

        // Convert nodes to Documents.
        Document schemaDoc = XmlUtil.newDocument(schemaNode , true);
        Document dataDoc = XmlUtil.newDocument(rootNode , true);

        String xmlns = XmlUtil.getNamespaceAttributeValue(dataDoc);
        String schemaLocationPropertyValue = xmlns + ' ' + "xmlschema";
        org.xml.sax.EntityResolver resolver = new XmlUtil.Resolver(schemaDoc);
        XmlUtil.validate(dataDoc, schemaLocationPropertyValue, resolver);

        return true;
    }


    /**
     * See next method for JavaDoc {@link #validateEmbeddedSchema(org.w3c.dom.Document, String, String)}.
     *
     */
    public static boolean validateEmbeddedSchema(
            byte[] bytes,
            String schemaTransform,
            String dataTransform)
            throws SAXException, IOException,
                ParserConfigurationException,
                TransformerException,
                TransformerConfigurationException {

        if (! XmlUtil.supportsValidation()) {
            return false;
        }

        Document doc = XmlUtil.parse(bytes);
        return validateEmbeddedSchema(doc, schemaTransform, dataTransform);
    }

    /**
     * A given Document has both content and an embedded schema (where
     * the schema has a single root node and the content has a single
     * root node - they are not interwoven). A single xsl transform is
     * provided to extract the schema part of the Document and another
     * xsl transform is provided to extract the content part and then
     * the content is validated against the schema.
     * <p>
     * If the content is valid, then nothing happens, but if the content
     * is not valid an execption is thrown (currently a RuntimeException).
     * <p>
     * When Mondrian moves to Java 5 or includes the JAXP 1.3 jar, then
     * there is a utility in JAXP that does something like this (but allows
     * for multiple schema/content parts).
     *
     */
    public static boolean validateEmbeddedSchema(
            Document doc,
            String schemaTransform,
            String dataTransform)
            throws SAXException, IOException,
                ParserConfigurationException,
                TransformerException,
                TransformerConfigurationException {

        if (! XmlUtil.supportsValidation()) {
            return false;
        }

        Node dataDoc = XmlUtil.transform(doc,
            new BufferedReader(new StringReader(dataTransform)));
        if (dataDoc == null) {
            LOGGER.debug("XmlaSupport.validateEmbeddedSchema: dataDoc is null");
            return false;
        }
        if (! dataDoc.hasChildNodes()) {
            LOGGER.debug("XmlaSupport.validateEmbeddedSchema: dataDoc has no children");
            return false;
        }
        String dataStr = XmlUtil.toString(dataDoc, false);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("XmlaSupport.validateEmbeddedSchema: dataDoc:\n="+dataStr);
        }
        if (! (dataDoc instanceof Document)) {
            LOGGER.warn("XmlaSupport.validateEmbeddedSchema: dataDoc not Document");
            return false;
        }


        Node schemaDoc = XmlUtil.transform(doc,
            new BufferedReader(new StringReader(schemaTransform)));
        if (schemaDoc == null) {
            LOGGER.debug("XmlaSupport.validateEmbeddedSchema: schemaDoc is null");
            return false;
        }
        if (! schemaDoc.hasChildNodes()) {
            LOGGER.debug("XmlaSupport.validateEmbeddedSchema: schemaDoc has no children");
            return false;
        }
        String schemaStr = XmlUtil.toString(schemaDoc, false);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("XmlaSupport.validateEmbeddedSchema: schemaDoc:\n="+schemaStr);
        }
        if (! (schemaDoc instanceof Document)) {
            LOGGER.warn("XmlaSupport.validateEmbeddedSchema: schemaDoc not Document");
            return false;
        }


        String xmlns = XmlUtil.getNamespaceAttributeValue((Document)dataDoc);
        String schemaLocationPropertyValue = xmlns + ' ' + "xmlschema";
        org.xml.sax.EntityResolver resolver = new XmlUtil.Resolver(schemaStr);
        XmlUtil.validate(dataStr, schemaLocationPropertyValue, resolver);

        return true;
    }

    public static Document transformSoapXmla(
        Document doc, String[][] namevalueParameters, String ns)
        throws SAXException, IOException,
        ParserConfigurationException,
        TransformerException
    {
        Node node = XmlUtil.transform(
            doc,
            new BufferedReader(new StringReader(getXmlaTransform(ns))),
            namevalueParameters);

        return (node instanceof Document) ? (Document) node : null;
    }


    /**
     * Reads a file line by line, adds a '\n' after each line and
     * returns in a String.
     *
     */
    public static String readFile(File file) throws IOException {
        StringBuilder buf = new StringBuilder(1024);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                buf.append(line);
                buf.append('\n');
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }

        return buf.toString();
    }


    private XmlaSupport() {}
}
