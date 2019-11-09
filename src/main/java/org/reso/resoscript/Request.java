package org.reso.resoscript;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Request {
    private String name;
    private String outputFile;
    private String url;
    private Date lastRun;
    private Integer lastRunCount;
    private Date createdOn;

    /**
     * Public constructor requires both outputFile and url. Remaining Request properties
     * may be set individually after Request has been instantiated.
     *
     * @param outputFile
     * @param url
     */
    public Request(String outputFile, String url) {
        setOutputFile(outputFile);
        setUrl(url);
    }

    /**
     * Provides null handling for getting the requested item
     * @param name the name of the item to get
     * @param node the nod to get the named item from
     * @return the named item, if present. Otherwise null
     */
    private static String safeGetNamedItem(String name, Node node) {
        Node named = node.getAttributes().getNamedItem(name);
        return named != null ? named.getNodeValue() : null;
    }

    /**
     * Loads the requests from the given File as an Observable List of Requests
     * @param file the file containing the requests
     * @return an Observable list of requests, or an exception is thrown
     */
    public static List<Request> loadFromRESOScript(File file) {
        final String REQUESTS_KEY = "Requests";
        ArrayList<Request> requests = new ArrayList<>();

        try {
            FileInputStream fileIS = new FileInputStream(file);
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(fileIS);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/OutputScript/" + REQUESTS_KEY + "/node()";
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
            Node node;
            String name, outputFile, url;
            Request request;

            for (int i = 0; i < nodes.getLength(); i++) {
                node = nodes.item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    outputFile = safeGetNamedItem(FIELDS.OUTPUT_FILE, node);
                    url = safeGetNamedItem(FIELDS.URL, node);

                    request = new Request(outputFile, url);

                    name = safeGetNamedItem(FIELDS.NAME, node);
                    request.setName(name == null ? outputFile : name);

                    requests.add(request);

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return requests;
    }

    /**
     * Name getter
     * @return the name of the request
     */
    public String getName() {
        return name;
    }

    /**
     * Name setter
     * @param name the name of the request
     */
    private void setName(String name) {
        this.name = name;
    }

    /**
     * Output file getter
     * @return the name of the output file for the request
     */
    public String getOutputFile() {
        return outputFile;
    }

    /**
     * Output file setter
     * @param outputFile the name of the output file for the request (required, not null)
     */
    private void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * URL getter
     * @return the URL for the request, or null
     */
    public String getUrl() {
        return url;
    }

    /**
     * URL setter
     * @param url the URL for the request, or null
     */
    private void setUrl(String url) {
        this.url = url;
    }

    /**
     * Last run getter
     * @return the date the request was last run, or null
     */
    public Date getLastRun() {
        return lastRun;
    }

    /**
     * Last run setter
     * @param lastRun the date the request was last run, or null
     */
    public void setLastRun(Date lastRun) {
        this.lastRun = lastRun;
    }

    /**
     * Last run count getter
     * @return the number of of records returned the last time this request was run, or null
     */
    public Integer getLastRunCount() {
        return lastRunCount;
    }

    /**
     * Last run count setter
     * @param lastRunCount the number of records returned the last time this request was run, or null
     */
    public void setLastRunCount(Integer lastRunCount) {
        this.lastRunCount = lastRunCount;
    }

    /**
     * Date created getter
     * @return the date the request was created, or null
     */
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * Date created setter
     * @param createdOn the date the request was created, or null
     */
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    private static final class FIELDS {
        static final String NAME = "Name";
        static final String OUTPUT_FILE = "OutputFile";
        static final String URL = "Url";
        static final String LAST_RUN = "LastRun";
        static final String LAST_RUN_COUNT = "LastRunCount";
        static final String CREATED_ON = "CreatedOn";
    }
}
