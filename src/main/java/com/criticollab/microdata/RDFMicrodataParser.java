package com.criticollab.microdata;


import info.aduna.net.ParsedURI;
import org.apache.commons.io.input.ReaderInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openrdf.model.*;
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
            processItem(element, null, null);
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
            String[] itemtypes = itemElement.attr("itemtype").split(" ");
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
        String vocab = null;
        if (primaryMicrodataType != null) {
            MicrodataRegistry.RegistryEntry registryEntry = getRegistry().match(primaryMicrodataType);

            if (registryEntry != null) {
                vocab = registryEntry.getPrefixURI();
            } else if (primaryMicrodataType != null && primaryMicrodataType.length() > 0) {
       /* 7. Otherwise,if type is not empty, construct vocab by removing everything following the last
            SOLIDUS U +002F ("/") or NUMBER SIGN U +0023 ("#") from the path component of type.
        */
                int index = primaryMicrodataType.lastIndexOf('#');
                if (index != -1) {
                    vocab = primaryMicrodataType.substring(0, index + 1);
                } else {
                    index = primaryMicrodataType.lastIndexOf('/');
                    if (index != -1)
                        vocab = primaryMicrodataType.substring(0, index + 1);
                }
            }
        }
        currentVocabulary = vocab;
        currentItemType = primaryMicrodataType;
        List<Element> itemProperties = findItemProperties(itemElement);
        //            For each element element that has one or more property names and is one of the properties of the item item run the following substep:
        for (Element itemProperty : itemProperties) {
            logger.info("itemProperty: {}" + itemProperty);
            //              For each name in the element's property names, run the following substeps:
            for (String name : itemProperty.attr("itemprop").split(" ")) {
//              Let context be a copy of evaluation context with current type set to type.
                //SES: let's not.
//                  Let predicate be the result of generate predicate URI using context and name.
                String predicate = generatePredicateURI(name,currentItemType,currentVocabulary);
                Value value=null;
                if(itemProperty.hasAttr("itemscope")) {
             // If value is an item, then generate the triples for value using context. Replace value by the subject returned from those steps.
                    value =  processItem(itemProperty,currentItemType,currentVocabulary);
                }  else {
                    value = getLiteralValue(itemProperty);
                }
//                    Let value be the property value of element.
//                    Generate the following triple:
                rdfHandler.handleStatement(createStatement(subject,createURI(predicate),value));
//            subject subject predicate predicate object value
//            If an entry exists in the registry for name in the vocabulary associated with vocab having the key subPropertyOf or equivalentProperty, for each such value equiv, generate the following triple:
//            subject subject predicate equiv object value

            }

        }
        return subject;
    }

    private Literal getLiteralValue(Element element) throws RDFParseException {
        return createLiteral(element.text(), null, null);
    }

    String generatePredicateURI(String name, String currentType,String currentVocabulary) {
//        If name is an absolute URL, return name as a URI reference.
        ParsedURI parsedURI = new ParsedURI(name);
        if(parsedURI.isAbsolute()) {
            return name;
        }

//        If current type from context is null, there can be no current vocabulary. Return the URI reference that is the document base with its fragment set to the canonicalized fragment value of name.

        if(currentType == null) {
            return document.baseUri() + ("#") + name;
        }
//        Set expandedURI to the URI reference constructed by appending the canonicalized fragment value of name to current vocabulary, separated by a U+0023 NUMBER SIGN character ("#") unless the current vocabulary ends with either a U+0023 NUMBER SIGN character ("#") or SOLIDUS U+002F ("/").
//                Return expandedURI.
        //TODO:FIXME
        if(currentVocabulary.endsWith("/") || currentVocabulary.endsWith("#")) {
            return currentVocabulary + name;
        } else {
            return currentVocabulary + "#" + name;
        }

    }
    List<Element> findItemProperties(Element root) {
//        Let results, memory, and pending be empty lists of elements.
//
        List<Element> results = new ArrayList<>();
        Set<Element> memory = new HashSet<>();
        Queue<Element> pending = new ArrayDeque<>();
        //        Add the element root to memory.
        memory.add(root);
//
//        Add the child elements of root, if any, to pending.
        pending.addAll(root.children());

//
//        If root has an itemref attribute, split the value of that itemref attribute on spaces.

        if(root.hasAttr("itemref")) {
            String ids[] = root.attr("itemref").split(" ");
            // For each resulting token ID, if there is an element in the home subtree of root with the ID ID,
            // then add the first such element to pending.
            for (String id : ids) {
                Elements foundIds = document.select(String.format("[id=%s]", id));
                if(foundIds.size() >0) {
                    pending.add(foundIds.get(0));
                }
            }
        }


//                Loop: If pending is empty, jump to the step labeled end of loop.
           while(!pending.isEmpty()) {
//                Remove an element from pending and let current be that element.
               Element current = pending.remove();
//        If current is already in memory, there is a microdata error; return to the step labeled loop.
//        Add current to memory.

               if(!memory.add(current)) {
                  continue;
               }
//
//
//        If current does not have an itemscope attribute, then: add all the child elements of current to pending.
               if(!current.hasAttr("itemscope"))    {
                   pending.addAll(current.children());
               }
//       If current has an itemprop attribute specified and has one or more property names, then add current to results.
//
               String itemprop = current.attr("itemprop");
               if(itemprop.length() >0) {
                   results.add(current);
               }
           }

//        End of loop: Sort results in tree order.
//                Return results.
//
       return results;
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

