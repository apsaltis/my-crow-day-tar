package com.criticollab.microdata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Whitebox Unit tests for RDFMicrodataParser
 */
public class RDFMicrodataParserTest {

    @Test
    public void testFindTopLevelElements() throws IOException {
        URL url = getClass().getResource("/w3c-microdata-rdf-tests/sdo_eg_md_11.html");

        Document document = Jsoup.parse(url.openStream(), "UTF-8", url.toString());
        RDFMicrodataParser ex = new RDFMicrodataParser();
        List<Element> elements = ex.findTopLevelItems(document);


        assertEquals("top-level-items count", 2, elements.size());
        Iterator<Element> it = elements.iterator();
        Element e;
        e = it.next();
        assertEquals("1st tag is body", "body",e.tagName());
        assertEquals("1st itemtype is WebPage", "http://schema.org/WebPage",e.attr("itemtype"));
        assertTrue("itemscope in 1st", e.hasAttr("itemscope"));
        assertFalse("no itemprop in 1st", e.hasAttr("itemprop"));
        e = it.next();
        assertEquals("2nd tag is div", "div",e.tagName());
        assertEquals("2nd itemtype is Book", "http://schema.org/Book",e.attr("itemtype"));
        assertTrue("itemscope in 2nd", e.hasAttr("itemscope"));
        assertFalse("no itemprop in 2nd", e.hasAttr("itemprop"));

    }
    @Test
    public void testGetLangTag() throws IOException {
        URL url = getClass().getResource("/com/criticollab/test-lang.html");

        Document document = Jsoup.parse(url.openStream(), "UTF-8", url.toString());
        RDFMicrodataParser ex = new RDFMicrodataParser();
        ex.setDocument(document);
        checkLangOfElement(ex, ".e1", "es");
        checkLangOfElement(ex, ".e2", "en");
        checkLangOfElement(ex, ".e3", "de");
        checkLangOfElement(ex, ".e4", null);
        checkLangOfElement(ex, "html", null);

    }

    private void checkLangOfElement(RDFMicrodataParser ex, String s, String expected) {
        Element e1 =ex.getDocument().select(s).first();
        assertEquals("get language of " + s , expected,ex.getLang(e1));
    }

}