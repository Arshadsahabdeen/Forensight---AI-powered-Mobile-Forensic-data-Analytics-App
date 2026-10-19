package com.forensics.platform.ufdr;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Hardened DOM parsing for untrusted evidence XML: DOCTYPE declarations, external entities,
 * external DTD/schema access and XInclude are all disabled (prevents XXE and entity expansion).
 */
public final class XMLParser {

    private XMLParser() {
    }

    public static Document parse(InputStream input) throws IOException, EvidenceFormatException {
        try {
            DocumentBuilder builder = factory().newDocumentBuilder();
            builder.setErrorHandler(new SilentErrorHandler());
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are not allowed");
            });
            Document document = builder.parse(input);
            document.getDocumentElement().normalize();
            return document;
        } catch (SAXParseException ex) {
            if (isDoctype(ex)) {
                throw new EvidenceFormatException("XML DOCTYPE declarations are not allowed in evidence files.");
            }
            throw new EvidenceFormatException("Malformed XML at line " + ex.getLineNumber() + ", column " + ex.getColumnNumber() + ".");
        } catch (SAXException ex) {
            throw new EvidenceFormatException("The XML could not be parsed safely.");
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("Secure XML parser configuration is unavailable", ex);
        }
    }

    private static DocumentBuilderFactory factory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        return factory;
    }

    private static boolean isDoctype(SAXParseException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("DOCTYPE");
    }

    private static final class SilentErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) {
            // ignore warnings
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
