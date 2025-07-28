package com.shnitko.exchange_rates_bot.service;

import com.shnitko.exchange_rates_bot.bot.ExchangeRatesBot;
import com.shnitko.exchange_rates_bot.client.NbpClient;
import com.shnitko.exchange_rates_bot.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.StringReader;

@Service
public class ExchangeRatesServiceImpl implements ExchangeRatesService {
    private static final Logger LOG = LoggerFactory.getLogger(ExchangeRatesServiceImpl.class);

    private static final String PATH = "//Rate[Code/text()='%s']/Mid";
//    private static final String EUR_PATH = "/ValCurs//Valute[@ID='R01239']/Value"; // for nb rus


    private final NbpClient client;

    @Autowired
    public ExchangeRatesServiceImpl(NbpClient client) {
        this.client = client;
    }

    @Override
    public double getExchangeRate(String currency) throws ServiceException {
        var xml = client.getCurrencyRatesXML();
        if (currency.equals("PLN")) {
            return 1.0;
        }
        if (isCurrencyAvailable(currency)){
            return Double.parseDouble(extractCurrencyValueFromXML(xml, String.format(PATH, currency)));
        } else {
            throw new ServiceException("Unknown currency");
        }
    }

    @Override
    public double convert(String fromCur, String toCur, double value) throws ServiceException {
        if (fromCur.equals(toCur)) return value;
        return getExchangeRate(fromCur) * value / getExchangeRate(toCur);
    }

    public boolean isCurrencyAvailable(String currencyCode) {
        if (currencyCode.equals("PLN")) return true;
        try {
            String xml = client.getCurrencyRatesXML();
            String xpath = String.format(PATH, currencyCode);
            String value = extractCurrencyValueFromXML(xml, xpath);
            return value != null && !value.trim().isEmpty();
        } catch (ServiceException e) {
            LOG.error("Error checking currency availability: {}", currencyCode, e);
            return false;
        }
    }

    private static String extractCurrencyValueFromXML(String xml, String xpathExpression) throws ServiceException {
        if (xml == null || xml.trim().isEmpty()) {
            throw new ServiceException("Empty XML content");
        }

        try (StringReader reader = new StringReader(xml)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);

            // Enable secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            DocumentBuilder builder = factory.newDocumentBuilder();

            // Add error handler to capture parsing issues
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXException {
                    LOG.error("XML parsing error", e);
                    throw e;
                }
            });

            InputSource source = new InputSource(reader);
            Document doc = builder.parse(source);
            doc.getDocumentElement().normalize();

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xPath = xPathFactory.newXPath();
            XPathExpression expr = xPath.compile(xpathExpression);

            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            if (node == null) {
                throw new ServiceException("Currency node not found for XPath: " + xpathExpression);
            }
            return node.getTextContent();
        } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
            throw new ServiceException("XML processing error", e);
        } catch (IOException e) {
            throw new ServiceException("IO error during XML parsing", e);
        }
    }
}

