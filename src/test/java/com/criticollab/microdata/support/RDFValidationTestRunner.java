package com.criticollab.microdata.support;
/**
 * Created by ses on 12/12/14.
 */

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.openrdf.model.*;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.rio.*;
import org.openrdf.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class RDFValidationTestRunner extends Runner {
    private static final String RDFT = "http://www.w3.org/ns/rdftest#";
    @SuppressWarnings("UnusedDeclaration")
    private static Logger logger = LoggerFactory.getLogger(RDFValidationTestRunner.class);

    private String manifestResourcePath;
    private URL manifestResource;
    private Class testClass;
    private Object testInstance;
    private Method testMethod;
    private static final String MF_BASE = "http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#";
    private static final ValueFactory vf = ValueFactoryImpl.getInstance();
    private static final URI RDFT_REGISTRY = vf.createURI(RDFT, "registry");
    private static final URI MF_RESULT = vf.createURI(MF_BASE, "result");
    private static final URI MF_ACTION = vf.createURI(MF_BASE, "action");
    private static final URI MF_MANIFEST = vf.createURI(MF_BASE, "Manifest");
    private static final URI MF_ENTRIES = vf.createURI(MF_BASE, "entries");
    private static final URI MF_NAME = vf.createURI(MF_BASE, "name");
    private Description suiteDescription;
    private Map<Description, ManifestTestEntry> testCases = new HashMap<>();


    public RDFValidationTestRunner(Class testClass) throws IllegalAccessException, InstantiationException, NoSuchMethodException, RDFParseException, IOException, RDFHandlerException {
        this.testClass = testClass;
        testMethod = testClass.getMethod("runTest", String.class, URL.class, URL.class,URL.class);
        testInstance = testClass.newInstance();
        ManifestPath manipath = (ManifestPath) testClass.getAnnotation(ManifestPath.class);
        manifestResourcePath = manipath.value();
        manifestResource = testInstance.getClass().getResource(manifestResourcePath);

    }


    @Override
    public Description getDescription() {
        suiteDescription = Description.createSuiteDescription("RDF Validation Tests from " + manifestResourcePath);
        try {
            addTestsFromManifest();
            return suiteDescription;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addTestsFromManifest() throws RDFParseException, IOException, RDFHandlerException {
        InputStream stream = testClass.getResourceAsStream(manifestResourcePath);
        String baseURI = manifestResource.toString();
        Model model = parseTurtle(stream, baseURI);
        Model statements = model.filter(null, RDF.TYPE, MF_MANIFEST);
        for (Statement statement : statements) {
            Resource manifest = statement.getSubject();
            for (Statement entriesStatements : model.filter(manifest, MF_ENTRIES, null)) {
                addTestForManifestEntry(model, (Resource) entriesStatements.getObject());
            }
        }
    }

    public static Model parseTurtle(InputStream stream, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        RDFParser parser = Rio.createParser(RDFFormat.TURTLE, vf);
        Model model = new LinkedHashModel();
        StatementCollector collector = new StatementCollector(model);
        parser.setRDFHandler(collector);
        parser.parse(stream, baseURI);
        return model;
    }

    private void addTestForManifestEntry(Model model, Resource entry) throws MalformedURLException {
        if (logger.isDebugEnabled()) {
            Model entryStatements = model.filter(entry, null, null);
            logger.debug("======= {} =======", entry.toString());
            for (Statement statement : entryStatements) {
                logger.debug("entry statement {}", statement.toString());
            }
            logger.debug("------");
        }
        String comment = getValue(model, entry, RDFS.COMMENT).stringValue();
        String name = getValue(model, entry, MF_NAME).stringValue();
        Resource action = (Resource) getValue(model, entry, MF_ACTION);
        Resource result = (Resource) getValue(model, entry, MF_RESULT);
        Value registry = getValue(model, entry, RDFT_REGISTRY) ;
        String prettyName = String.format("%s: %s", name, comment);
        Description testDescription = Description.createTestDescription(testClass, prettyName);
        suiteDescription.addChild(testDescription);
        URL actionURL = new URL(action.stringValue());
        URL resultURL = new URL(result.stringValue());
        URL registryURL = null;
        if(registry != null) {
            String registryString=null;
            registryString = registry.stringValue();
            registryURL = new URL(registryString);
        }   else {
            registryURL = testClass.getResource("/w3c-microdata-rdf-tests/test-registry.json");
        }
        logger.debug("name {}, comment {}, action {}, result {}, registry {}, registry {}", name, comment, action, result, registry,registryURL);
        if (testCases.put(testDescription, new ManifestTestEntry(name, comment, actionURL, resultURL,registryURL)) != null) {
            throw new IllegalStateException("already had a test case named " + name);
        }
        ;
    }

    private Value getValue(Model model, Resource subject, URI property) {
        Model values = model.filter(subject, property, null);
        if (values.size() != 1) {
            return null;
        }
        return values.objectValue();
    }

    /**
     * Run the tests for this runner.
     *
     * @param notifier will be notified of events while tests are being run--tests being
     *                 started, finishing, and failing
     */
    @Override
    public void run(RunNotifier notifier) {
        for (Description testDescription : suiteDescription.getChildren()) {
            ManifestTestEntry testCase = testCases.get(testDescription);
            assert testCase != null;
            notifier.fireTestStarted(testDescription);
            URL src = testCase.getAction();
            URL expected = testCase.getResult();
            try {
                testMethod.invoke(testInstance, testCase.getName(), src, expected,testCase.getRegistry());
            } catch (InvocationTargetException e) {
                //logger.error("Caught Exception", e); //To change body of catch statement use File | Settings | File Templates.
                Failure failure = new Failure(testDescription, e.getCause());
                notifier.fireTestFailure(failure);
            } catch (IllegalAccessException e) {
                Failure failure = new Failure(testDescription, e.getCause());
                notifier.fireTestFailure(failure);
                logger.error("Caught Exception", e); //To change body of catch statement use File | Settings | File Templates.
            } finally {
                notifier.fireTestFinished(testDescription);

            }

        }

    }

    private static class ManifestTestEntry {
        String name;
        String comment;
        URL action;
        URL result;
        private URL registry;

        public ManifestTestEntry(String name, String comment, URL action, URL result, URL registry) {
            this.name = name;
            this.comment = comment;
            this.action = action;
            this.result = result;
            this.registry = registry;
        }

        public String getName() {
            return name;
        }


        public URL getAction() {
            return action;
        }

        public String getComment() {
            return comment;
        }

        public URL getResult() {
            return result;
        }


        public URL getRegistry() {
            return registry;
        }
    }
}
