package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.PdfExtractionException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a GROBID {@code processHeaderDocument} TEI response (TRD §6.1, item 1): title,
 * authors as {@code forename + " " + surname}, and abstract paragraphs joined with a
 * space. XPath expressions match on {@code local-name()} so they are unaffected by the
 * TEI default namespace declared on the document and (redundantly, in some GROBID
 * output) on the {@code <abstract><div>} element.
 *
 * <p>Package-private and stateless: {@link GrobidPdfMetadataExtractor} is the only
 * caller, and this class does no I/O of its own, which is what makes it testable
 * directly against a fixture TEI file without an HTTP server.
 */
final class GrobidTeiParser {

    private GrobidTeiParser() {
    }

    /** The raw fields pulled out of the TEI, before {@link IngestionTextCleaner} runs. */
    record TeiExtraction(String title, List<String> authors, String abstractText) {
    }

    static TeiExtraction parse(String teiXml) {
        Document document = parseXml(teiXml);
        XPath xPath = XPathFactory.newInstance().newXPath();

        String title = stringOf(xPath, document, "//*[local-name()='titleStmt']/*[local-name()='title'][1]");
        List<String> authors = authorsOf(xPath, document);
        String abstractText = abstractOf(xPath, document);

        return new TeiExtraction(title, authors, abstractText);
    }

    private static Document parseXml(String teiXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // GROBID's own TEI output is trusted infrastructure, but disabling DTDs
            // and external entities costs nothing and rules out XXE by construction.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(teiXml)));
        } catch (ParserConfigurationException | org.xml.sax.SAXException | java.io.IOException e) {
            throw new PdfExtractionException("Failed to parse GROBID TEI response", e);
        }
    }

    private static List<String> authorsOf(XPath xPath, Document document) {
        NodeList authorNodes = nodeListOf(xPath, document,
                "//*[local-name()='sourceDesc']//*[local-name()='analytic']/*[local-name()='author']");
        List<String> authors = new ArrayList<>();
        for (int i = 0; i < authorNodes.getLength(); i++) {
            Node author = authorNodes.item(i);
            String forename = stringOf(xPath, author, ".//*[local-name()='forename'][1]");
            String surname = stringOf(xPath, author, ".//*[local-name()='surname'][1]");
            String fullName = (forename + " " + surname).trim();
            if (!fullName.isBlank()) {
                authors.add(fullName);
            }
        }
        return authors;
    }

    private static String abstractOf(XPath xPath, Document document) {
        NodeList paragraphs = nodeListOf(xPath, document,
                "//*[local-name()='profileDesc']/*[local-name()='abstract']//*[local-name()='p']");
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < paragraphs.getLength(); i++) {
            if (i > 0) {
                joined.append(' ');
            }
            joined.append(paragraphs.item(i).getTextContent());
        }
        return joined.toString().trim();
    }

    private static String stringOf(XPath xPath, Object context, String expression) {
        try {
            String value = (String) xPath.evaluate(expression, context, XPathConstants.STRING);
            return value == null ? "" : value.trim();
        } catch (XPathExpressionException e) {
            throw new PdfExtractionException("Invalid XPath expression: " + expression, e);
        }
    }

    private static NodeList nodeListOf(XPath xPath, Object context, String expression) {
        try {
            return (NodeList) xPath.evaluate(expression, context, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new PdfExtractionException("Invalid XPath expression: " + expression, e);
        }
    }
}
