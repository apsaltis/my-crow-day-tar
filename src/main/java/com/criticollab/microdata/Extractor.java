package com.criticollab.microdata;


import org.jsoup.nodes.Document;
import org.openrdf.model.Model;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Extractor {
    @SuppressWarnings("UnusedDeclaration")
    private static Logger logger = LoggerFactory.getLogger(Extractor.class);

    public static Model extract(Document document) {
        Model m = new  LinkedHashModel();
        ValueFactory vf = ValueFactoryImpl.getInstance();
        String sdo = "http://schema.org/";
        m.add(vf.createBNode("a"),vf.createURI(sdo, "name"), vf.createLiteral("Gregg Kellogg"));
        m.add(vf.createBNode("a"), RDF.TYPE,vf.createURI(sdo,"Person"));
        return m;
    }
}
