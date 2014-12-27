package com.criticollab.microdata;

import com.criticollab.microdata.support.ManifestPath;
import com.criticollab.microdata.support.RDFValidationTestRunner;
import com.criticollab.microdata.support.SortedBufferedGroupingRDFHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.ComparisonFailure;
import org.junit.runner.RunWith;
import org.openrdf.model.Model;
import org.openrdf.model.Namespace;
import org.openrdf.model.util.ModelUtil;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;
import org.openrdf.rio.turtle.TurtleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;

@RunWith(RDFValidationTestRunner.class)
@ManifestPath("/w3c-microdata-rdf-tests/manifest.ttl")
public class W3CMicrodataToRDFExtractionTests {
    private static final String MAGIC_BASE = "http://w3c.github.io/microdata-rdf/tests/";

    static Logger logger = LoggerFactory.getLogger(W3CMicrodataToRDFExtractionTests.class);

    public void runTest(String name, URL src, URL result,URL registry) throws IOException, RDFParseException, RDFHandlerException {
        logger.debug("run Test method called with {} , {}, {}", name, src, result);
        //Document doc = Jsoup.parse(src.openStream(),"UTF-8",src.toString());
        Document doc = Jsoup.parse(src.openStream(), "UTF-8", getMagicalizedBase(src));
        RDFMicrodataParser ex = new RDFMicrodataParser();
        ex.getParserConfig().set(RDFMicrodataParser.REGISTRY, registry);
        Model actual = ex.extract(doc);
        Model expected = RDFValidationTestRunner.parseTurtle(result.openStream(), getMagicalizedBase(result));
        compareModels(doc, expected, actual);
    }

    private String getMagicalizedBase(URL realURI) {
        String s = realURI.toString();
        String lastPart = s.substring(s.lastIndexOf('/') + 1);
        return MAGIC_BASE + lastPart;
    }

    private void compareModels(Document doc, Model expected, Model actual) throws RDFHandlerException {
        // logger.debug("doc:\n{}", doc);
        // logger.debug("model:\n{}", expected);
        for (Namespace namespace : expected.getNamespaces()) {
            actual.setNamespace(namespace);
        }

        boolean b = ModelUtil.equals(expected, actual);
        if (!b) {
            throw new ComparisonFailure("Turtle comparison failed", toTurtleString(expected), toTurtleString(actual));
        }
    }

    private String toTurtleString(Model model) throws RDFHandlerException {
        StringWriter buf = new StringWriter();
        RDFHandler out = new TurtleWriter(buf);
        out = new SortedBufferedGroupingRDFHandler(8192, out);
        Rio.write(model, out);
        buf.flush();
        return buf.toString();
    }
}