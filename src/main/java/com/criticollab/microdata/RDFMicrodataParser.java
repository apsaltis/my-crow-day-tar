package com.criticollab.microdata;


import info.aduna.net.ParsedURI;
import org.apache.commons.io.input.ReaderInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RioSetting;
import org.openrdf.rio.helpers.RDFParserBase;
import org.openrdf.rio.helpers.RioSettingImpl;
import org.openrdf.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.*;

public class RDFMicrodataParser extends RDFParserBase {
    private static final URI STANDARD_URI = ValueFactoryImpl.getInstance().createURI("http://www.w3.org/ns/formats/md");
    //******************* RIO RDF Parser Implementation
    public static final RDFFormat FORMAT = new RDFFormat("HTML5-Microdata2", Arrays.asList("text/html"),
            Charset.forName("UTF-8"), Arrays.asList("html"), STANDARD_URI, RDFFormat.NO_NAMESPACES,
            RDFFormat.NO_CONTEXTS);
    private static final String CHARSET_NAME = "UTF_16BE";
    private static final Charset CHARSET = Charset.forName(CHARSET_NAME);
    private static final String ITEMID = "itemid";
    @SuppressWarnings("UnusedDeclaration")
    private static Logger logger = LoggerFactory.getLogger(RDFMicrodataParser.class);

    /**
     * The JSoup DOM from which to extract items
     */
    private Document document;
    private MicrodataRegistry registry;
    /**
     * A mapping from Item Elements to Resources. Part of the evaluation context defined
     * in the microdata-to-rdf spec)
     */
    private Map<Element, Resource> memory;
    /**
     * The URI of the current item being processed. Part of the evaluation context defined
     * in the microdata-to-rdf spec
     */
    /**
     * the prefixURI  for the current vocabulary, from the registry. Part of the evaluation context defined
     * in the microdata-to-rdf spec.
     */
    private static final RioSetting<Boolean> FAIL_ON_RELATIVE_ITEMIDS = new RioSettingImpl<>("com.criticollab.microdata.fail-on-relative-itemids",
            "Fail if relative ITEMID is encountered",
            Boolean.FALSE);
    private static final RioSetting<Boolean> FAIL_ON_RELATIVE_ITEMTYPES = new RioSettingImpl<>("com.criticollab.microdata.fail-on-relative-itemtypes",
            "Fail if relative ITEMTYPE is encountered",
            Boolean.FALSE);


    /**
     * Gets the RDF format that this parser can parse.
     */
    @Override
    public RDFFormat getRDFFormat() {
        return FORMAT;
    }


    public RDFMicrodataParser() {
    }


    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        Collection<RioSetting<?>> settings = super.getSupportedSettings();
        settings.add(FAIL_ON_RELATIVE_ITEMIDS);
        settings.add(FAIL_ON_RELATIVE_ITEMTYPES);
        return settings;
    }

    /**
     * Parses the data from the supplied InputStream, using the supplied baseURI
     * to resolve any relative URI references.
     *
     * @param in      The InputStream from which to read the data.
     * @param baseURI The URI associated with the data in the InputStream.
     * @throws java.io.IOException                 If an I/O error occurred while data was read from the InputStream.
     * @throws org.openrdf.rio.RDFParseException   If the parser has found an unrecoverable parse error.
     * @throws org.openrdf.rio.RDFHandlerException If the configured statement handler has encountered an
     *                                             unrecoverable error.
     */
    @Override
    public void parse(InputStream in, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        parse(in, null, baseURI);
    }

    /**
     * Parses the data from the supplied Reader, using the supplied baseURI to
     * resolve any relative URI references.  Since JSoup only takes bytes input streams,
     * we have to encode the contents so that jsoup can do its own decoding.
     *
     * @param reader  The Reader from which to read the data.
     * @param baseURI The URI associated with the data in the InputStream.
     * @throws java.io.IOException                 If an I/O error occurred while data was read from the InputStream.
     * @throws org.openrdf.rio.RDFParseException   If the parser has found an unrecoverable parse error.
     * @throws org.openrdf.rio.RDFHandlerException If the configured statement handler has encountered an
     *                                             unrecoverable error.
     */
    @Override
    public void parse(Reader reader, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        ReaderInputStream in = new ReaderInputStream(reader, CHARSET);
        parse(in, CHARSET_NAME, baseURI);
    }

    private void parse(InputStream in, String charsetName, String baseURI) throws IOException, RDFHandlerException, RDFParseException {
        setBaseURI(baseURI);
        document = Jsoup.parse(in, charsetName, baseURI);
        try {
            processDocument();
        } finally {
            clear();
        }

    }

    @Override
    protected void clear() {
        super.clear();
        document = null;
        memory = null;
    }


    public static Model extract(Document doc) throws RDFHandlerException, RDFParseException {
        RDFMicrodataParser parser = new RDFMicrodataParser();
        parser.document = doc;
        Model model = new LinkedHashModel();
        parser.setRDFHandler(new StatementCollector(model));
        parser.processDocument();
        parser.setRDFHandler(null);
        return model;
    }

    private void processDocument() throws RDFHandlerException, RDFParseException {
        memory = new IdentityHashMap<>();
        getRDFHandler().startRDF();
        for (Element element : findTopLevelItems(document)) {
            processItem(element, "", "");
        }
        getRDFHandler().endRDF();
    }


    Resource processItem(Element itemElement, String currentItemType, String currentVocabulary) throws RDFParseException, RDFHandlerException {
        logger.info("processing top level item in {} ", itemElement.nodeName());
        /*
        1. If there is an entry for item in memory, then let subject be the subject of that entry.
        Otherwise, if item has a global identifier and that global identifier is an absolute URL,
         let subject be that global identifier.
        Otherwise, let subject be a new blank node.
         */
        Resource subject = memory.get(itemElement);
        if (subject == null) {
            if (itemElement.hasAttr(ITEMID)) {
                String uriString = itemElement.attr(ITEMID);
                try {
                    subject = resolveURI(uriString);
                } catch (RDFParseException e) {
                    if (getParserConfig().get(FAIL_ON_RELATIVE_ITEMIDS)) {
                        reportFatalError(e);
                    } else {
                        reportWarning(e.getMessage());
                    }
                }
            }
        }
        if (subject == null) {
            subject = createBNode();
        }
        /*
           2. Add a mapping from item to subject in memory
         */

        memory.put(itemElement, subject);

    /*
     3. For each type returned from element.itemType of the element defining the item.
            1. If type is an absolute URL, generate the following triple:
                subject subject predicate rdf:type object type (as a URI reference)
     4. Set type to the first value returned from element.itemType of the element defining the item.

     */
        String primaryMicrodataType = null;

        if (itemElement.hasAttr("itemtype"))

        {
            String[] itemtypes = itemElement.attr("itemtype").split("\\s");
            for (String itemtype : itemtypes) {
                ParsedURI uri = new ParsedURI(itemtype);
                if (!uri.isAbsolute()) {
                    if (getParserConfig().get(FAIL_ON_RELATIVE_ITEMTYPES)) {
                        reportFatalError("encountered relative itemtype; " + itemtype);
                    }
                } else {
                    rdfHandler.handleStatement(createStatement(subject, RDF.TYPE, createURI(uri.toString())));
                    if (primaryMicrodataType == null) {
                        primaryMicrodataType = itemtype;
                    }
                }
            }
        }

        /*
            5. Otherwise, set type to current type from evaluation context if not empty.
         */
        if (primaryMicrodataType == null)

        {
            primaryMicrodataType = currentItemType;
        }
        /*
            6. If the registry contains a URI prefix that is a character for character match of type up to the
               length of the URI prefix, set vocab as that URI prefix.
        */
        String vocab = "";
        if (primaryMicrodataType != null) {
            MicrodataRegistry.RegistryEntry registryEntry = getRegistry().match(primaryMicrodataType);

            if (registryEntry != null) {
                vocab = registryEntry.getPrefixURI();
            } else if (primaryMicrodataType != null && primaryMicrodataType.length()>0){
       /* 7. Otherwise,if type is not empty, construct vocab by removing everything following the last
            SOLIDUS U +002F ("/") or NUMBER SIGN U +0023 ("#") from the path component of type.
        */
                int index = primaryMicrodataType.lastIndexOf('#');
                if (index != -1) {
                    vocab = primaryMicrodataType.substring(0, index+1);
                } else {
                    index = primaryMicrodataType.lastIndexOf('/');
                    if (index != -1)
                        vocab = primaryMicrodataType.substring(0, index+1);
                }
            }
        }
        currentVocabulary = vocab;

        return subject;
    }

    List<Element> findTopLevelItems(Document document) {
        return document.select("[itemscope]:not([itemprop])");
    }

    String getLang(Element element) {
        if (element.hasAttr("lang")) {
            return element.attr("lang");
        } else if (element.parent() != null) {
            return getLang((element.parent()));
        } else {
            return "";
        }
    }

    public Document getDocument() {
        return document;
    }


    public void setDocument(Document document) {
        this.document = document;
    }

    public MicrodataRegistry getRegistry() {
        if (registry == null) {
            registry = new MicrodataRegistry();
        }
        return registry;
    }

    public void setRegistry(MicrodataRegistry registry) {
        this.registry = registry;
    }
}

