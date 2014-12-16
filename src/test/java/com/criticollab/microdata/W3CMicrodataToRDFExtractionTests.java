package com.criticollab.microdata;

import com.criticollab.microdata.support.ManifestPath;
import com.criticollab.microdata.support.RDFValidationTestRunner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.runner.RunWith;
import org.openrdf.model.Model;
import org.openrdf.model.util.ModelUtil;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.fail;

@RunWith(RDFValidationTestRunner.class)
@ManifestPath("/w3c-microdata-rdf-tests/manifest.ttl")
public class W3CMicrodataToRDFExtractionTests {
    static Logger logger = LoggerFactory.getLogger(W3CMicrodataToRDFExtractionTests.class);

    public void runTest(String name, URL src, URL result) throws IOException, RDFParseException, RDFHandlerException {
        logger.debug("run Test method called with {} , {}, {}", name, src, result);
        Document doc = Jsoup.parse(src.openStream(),"UTF-8",src.toString());
        Model actual = Extractor.extract(doc);
        Model expected = RDFValidationTestRunner.parseTurtle(result.openStream(),result.toString());
        compareModels(doc, actual, expected);
    }

    private void compareModels(Document doc, Model actual, Model expected) {
        logger.debug("doc:\n{}", doc);
        logger.debug("model:\n{}", expected);

        boolean b = ModelUtil.equals(expected, actual);
        if(!b) {
           String msg =  String.format("comparison failed: doc=\n%s\n,expected:\n%s\nactual:\n%s",doc,expected,actual);
            fail(msg);
        }
    }
}