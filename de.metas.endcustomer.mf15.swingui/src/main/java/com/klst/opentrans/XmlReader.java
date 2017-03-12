/*
 * #%L
 * de.metas.endcustomer.mf15.swingui
 * %%
 * Copyright (C) 2016 klst GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package com.klst.opentrans;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.opentrans.xmlschema._2.DISPATCHNOTIFICATION;
import org.opentrans.xmlschema._2.DISPATCHNOTIFICATIONITEM;
import org.opentrans.xmlschema._2.OPENTRANS;
import org.opentrans.xmlschema._2.ORDER;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.klst.mf.opentrans.process.AvisPipedInputStream;

import de.metas.logging.LogManager;

public class XmlReader implements ErrorHandler {
	private static final Logger log = LogManager.getLogger(XmlReader.class);

	private static final String OPENTRANS_XSD_20 = "/opentrans_2_0.xsd";
	private static final String OPENTRANS_XSD_21 = "/opentrans_2_1.xsd";

	private static final boolean NAMESPACE_AWARENESS = true;

	private DocumentBuilderFactory dbFactory = null;
	private DocumentBuilder dBuilder = null;
	private XPath xpath = null;
	private Transformer xformer = null;
	private URL schema_xsd_url = null;
	private Schema schema = null;

	public static XmlReader newInstance() {
		return new XmlReader();
	}
	
	private XmlReader() {
		super();
		log.info("ctor()");
		
		/*
		 * welche Implementierung liegt vor? 
		 * Hinweis in {@link DocumentBuilderFactory.newInstance() } 
		 * <pre> java -Djaxp.debug=1 YourProgram .... 
		 * </pre> 
		 * oder so: 
		 * // System.setProperty("jaxp.debug","1");
		 * // log.fine("set jaxp.debug="+System.getProperty("jaxp.debug"));
		 */
		dbFactory = DocumentBuilderFactory.newInstance();
		dbFactory.setNamespaceAware(NAMESPACE_AWARENESS);
		schema_xsd_url = OPENTRANS.class.getResource(OPENTRANS_XSD_20);
		schema = XmlReader.loadSchema(schema_xsd_url);
		dbFactory.setValidating(true); // liefert in parseDocument():
		dbFactory.setSchema(schema); // When a Schema is non-null, a parser will use a validator created from it to validate documents before it passes information down to the application. 
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			log.info("ctor() parser.isNamespaceAware()={} , isValidating()={} , isXIncludeAware()={}"
					, dBuilder.isNamespaceAware(), dBuilder.isValidating(), dBuilder.isXIncludeAware() );
			dBuilder.setErrorHandler(this);
			XPathFactory xpathFactory = XPathFactory.newInstance(); // default
			xpath = xpathFactory.newXPath();
			// xformer = TransformerFactory.newInstance().newTransformer();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
	}

	public Node getOrder(Document doc) {
		Node node = null;
		// doc.getDocumentElement().getNodeName() // convenience attribute that allows direct access to the child
		Element e = doc.getDocumentElement();
		if("ORDER".equals(e.getNodeName())) { 
			NodeList nodes = doc.getChildNodes();
			log.info("getOrder done filter #comment. nodes#={}", nodes.getLength());
			for(int i=0;i<nodes.getLength();i++) { // #comment rausfiltern
				node = nodes.item(i);
				if("ORDER".equals(node.getNodeName())) {
					return node;
				}
			}
		} else {
			node = getOrder(e); 
		}
		return node;		
	}
	
	public Node getOrder(Node item) {
		log.info("getOrder: NodeName={}", item.getNodeName());
		Node node = null;
		try {
			NodeList nodes = item.getChildNodes();
			log.info("getOrder suchen... nodes#={}", nodes.getLength());
			for(int i=0;i<nodes.getLength();i++) { // #comment rausfiltern
				node = nodes.item(i);
				if("ORDER".equals(node.getNodeName())) {
					return node;
				}
			}
			// @TODO
			nodes = (NodeList)xpath.evaluate( "//ORDER", item, XPathConstants.NODESET );
			log.info("getOrder ***2ter Versuch*** nodes#={}", nodes.getLength());
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		return node;
	}
	
	public Object unmarshal(Node node, Class<?> classToBeBound) {
		Object o = null;
		try {
			JAXBContext jc = JAXBContext.newInstance( classToBeBound ); // JAXBException 
			Unmarshaller u = jc.createUnmarshaller();
			// Disabling schema validation while unmarshalling :
			u.setEventHandler(new IgnoringValidationEventHandler());
			u.setSchema(this.schema);
			o = u.unmarshal(node);
			if(classToBeBound.equals(ORDER.class)) {
				log.info("result is ORDER: Type={} Version={}", ((ORDER)o).getType(), ((ORDER)o).getVersion());
			} else {
				log.info("Type={}", o.getClass());
			}
		} catch (JAXBException e) {
			e.printStackTrace();
		}
		return o;
	}
	
	/*
	 * wg kompatibilität
	 */
	/**
	 * @deprecated
	 */
	public ORDER unmarshal(Node node) {
		return (ORDER)this.unmarshal(node, ORDER.class);
	}
	
	/**
	 * liest ein openTRANS-XML-Dokument und liefert das Document
	 * 
	 * @param uri
	 *            , Bsp DIR + "SOE-foo.xml"
	 * @return org.w3c.dom.Document
	 */
	public Document read(String uri) {
		log.info("let's read ... File-uri={}", uri);
		Document doc = null;
		try {
			File file = new File(uri);
			doc = parseDocument(file);
		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
		} finally {
		}
		return doc;
	}
	
	/**
	 * liefert org.w3c.dom.Document
	 * 
	 * @param is InputStream containing the content to be parsed.
	 * @return org.w3c.dom.Document
	 */
	public Document parseDocument(InputStream is) {
		Document document = null;
		try {
			document = dBuilder.parse(is);
			log.info("parseDocument(stream) XmlVersion={}, Root element={} #Elements={}"
					, document.getXmlVersion()
					, document.getDocumentElement().getNodeName()
					, document.getElementsByTagName("*").getLength()
					);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return document;
	}
	
	/**
	 * liefert org.w3c.dom.Document
	 * 
	 * @param uri The location of the content to be parsed.
	 * @return org.w3c.dom.Document
	 */
	public Document parseDocument(String uri) {
		Document document = null;
		try {
			document = dBuilder.parse(uri);
			log.info("parseDocument(uri) XmlVersion={}, Root element={} #Elements={}"
					, document.getXmlVersion()
					, document.getDocumentElement().getNodeName()
					, document.getElementsByTagName("*").getLength()
					);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return document;
	}
	
	/**
	 * liefert org.w3c.dom.Document
	 * 
	 * @param file The file containing the XML to parse.
	 * @return org.w3c.dom.Document
	 */
	private Document parseDocument(File file) {
		Document document = null;
		try {
			document = dBuilder.parse(file);
			log.info("parseDocument(file) XmlVersion={}, Root element={} #Elements={}"
					, document.getXmlVersion()
					, document.getDocumentElement().getNodeName()
					, document.getElementsByTagName("*").getLength()
					);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return document;
	}

	/*
	 * OPENTRANS_XSD_21 und die sub-xsd's befinden sich im opentrans.jar-file 
	 */
	private static Schema loadSchema(URL url) {
		log.info("loadSchema from url={}", url.toString());
		Schema schema = null;
		String language = XMLConstants.W3C_XML_SCHEMA_NS_URI;
		SchemaFactory sf = SchemaFactory.newInstance(language);		
		try {
			schema = sf.newSchema(url);
		} catch (SAXException e) {
			e.printStackTrace();
		}
		return schema;
	}

	/**
	 * liefert schema aus xsd
	 * @param uri , z.B. xsd-lib/opentrans.xsd
	 * @return javax.xml.validation.Schema
	 */
	/* Exceptions:
	 * 
	 * 1. ist nur OPENTRANS_XSD vorhanden (also keine abhängigen)
		ERROR - src-resolve: Cannot resolve the name 'bmecat:dtMLSTRING' to a(n) 'type definition' component.
		org.xml.sax.SAXParseException; lineNumber: 518; columnNumber: 52; src-resolve: Cannot resolve the name 'bmecat:dtMLSTRING' to a(n) 'type definition' component.
		    at org.apache.xerces.util.ErrorHandlerWrapper.createSAXParseException(Unknown Source)
	 * Lösung: es nutzt nichts bmecat-foo.xsd in XSD-Verz zu kopieren, denn in OPENTRANS_XSD steht: schemaLocation="bmecat_2005.xsd"
	 *  - bmecat-foo.xsd ins root-Ver des Projekts kopieren oder ins xsd-lib/ und  anpassen schemaLocation="bmecat_2005.xsd"
	 *  Konsequenterweis sollte OPENTRANS_XSD auch in dieses Verz
	 * 
	 * 2. dto für xmime und xmldsig
	 */
	public static Schema loadSchema(String uri) {
		log.info("let's loadSchema ... File-uri={}", uri);
		Schema schema = null;
		String language = XMLConstants.W3C_XML_SCHEMA_NS_URI;
		SchemaFactory sf = SchemaFactory.newInstance(language);
		File file = new File(uri);
		try {
			InputStream inputStream = new FileInputStream(file);
			schema = sf.newSchema(new StreamSource(inputStream));
//			schema.newValidator();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			if(log.isDebugEnabled()) e.printStackTrace();
			log.warn("xsd-file-uri={}",e.getMessage());
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return schema;
	}

	// wg. implements ErrorHandler
	@Override
	public void warning(SAXParseException exception) throws SAXException
	{
		log.warn("SAXParseException={}", exception.getMessage());
	}

	@Override
	public void error(SAXParseException exception) throws SAXException
	{
		log.error("SAXParseException={}", exception.getMessage());
	}

	@Override
	public void fatalError(SAXParseException exception) throws SAXException
	{
		log.error("SAXParseException={}", exception.getMessage());
	}

	// -------------------- TODO test auslagern
	static final String RESOURCES_DIR = "src/test/resources/";
	static final String SAMPLE_XML = "sample_order_opentrans_2_1_xml signature.xml";
	private static final String SOE_XML = "SOE-order_FH_31234_8566_2014-09-09-0.724.xml";
	private static final String DISPATCHNOTIFICATION_XML = "DESADV_13080388-8662.xml";

	public static void main(String[] args) {
//		XmlReader.loadSchema("opentrans_2_1.xsd");
		XmlReader reader = new XmlReader();
		Document doc = null;
		
		try {
			InputStream is = new AvisPipedInputStream(RESOURCES_DIR + DISPATCHNOTIFICATION_XML);
			doc = reader.parseDocument(is);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.info("main doc NodeType={} NodeName={} NodeValue={}", doc.getNodeType(), doc.getNodeName(), doc.getNodeValue());
		log.info("main DocumentElement NodeType={} NodeName={} NodeValue={}", doc.getDocumentElement().getNodeType(), doc.getDocumentElement().getNodeName(), doc.getDocumentElement().getNodeValue());
		DISPATCHNOTIFICATION avis = (DISPATCHNOTIFICATION)reader.unmarshal(doc.getDocumentElement(), DISPATCHNOTIFICATION.class);
		List<DISPATCHNOTIFICATIONITEM> avisItems = avis.getDISPATCHNOTIFICATIONITEMLIST().getDISPATCHNOTIFICATIONITEM();
		log.info("Avis Date={} #Items={}",avis.getDISPATCHNOTIFICATIONHEADER().getDISPATCHNOTIFICATIONINFO().getDISPATCHNOTIFICATIONDATE()
				, avisItems.size()
				);
	}

}
