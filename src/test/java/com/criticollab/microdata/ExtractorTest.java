package com.criticollab.microdata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.stream.Stream;

/**
 * Whitebox Unit tests for Extractor
 */
public class ExtractorTest {

    @Test
    public void testFindTopLevelElements() throws IOException {
        URL url = getClass().getResource("/w3c-microdata-rdf-tests/sdo_eg_md_11.html");

        Extractor ex = new Extractor();
        Stream<Element> stream = ex.findTopLevelItems(Jsoup.parse(url.openStream(),"UTF-8",url.toString()));
        stream.forEach(element -> System.out.println("element = " + element));

    }

}