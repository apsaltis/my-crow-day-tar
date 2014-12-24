package com.criticollab.microdata;/**
 * Created by ses on 12/22/14.
 */

import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RDFMicrodataParserFactory implements RDFParserFactory {
    @SuppressWarnings("UnusedDeclaration")
    private static Logger logger = LoggerFactory.getLogger(RDFMicrodataParserFactory.class);

    /**
     * Returns the RDF format for this factory.
     */
    @Override
    public RDFFormat getRDFFormat() {
        return RDFMicrodataParser.FORMAT;
    }

    /**
     * Returns a RDFParser instance.
     */
    @Override
    public RDFParser getParser() {
        return new RDFMicrodataParser();
    }
}
