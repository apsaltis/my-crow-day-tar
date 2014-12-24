package com.criticollab.microdata;/**
 * Created by ses on 12/24/14.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MicrodataRegistry {
    @SuppressWarnings("UnusedDeclaration")
    private static Logger logger = LoggerFactory.getLogger(MicrodataRegistry.class);
    private static ObjectMapper jsonMapper = new ObjectMapper();
    private static final URL defaultRegisryURL;

    public static class PropertyAttributes {
        private Map<String, Object> attributes = new HashMap<>();

        PropertyAttributes(Map<String, Object> attributes) {
            this.attributes.putAll(attributes);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PropertyAttributes{");
            sb.append("kvpairs=").append(attributes);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class RegistryEntry {
        private Map<String, PropertyAttributes> properties = new HashMap<>();
        private String prefixURI;

        public RegistryEntry(String prefixURI) {
            this.prefixURI = prefixURI;
        }

        public void addProperty(String propertyName, PropertyAttributes attributes) {
            properties.put(propertyName, attributes);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("RegistryEntry{");
            sb.append("prefixUri='").append(prefixURI).append('\'').append(',');
            sb.append("properties={");
            for (Map.Entry<String, PropertyAttributes> propertyAttributesEntry : properties.entrySet()) {
                sb.append(propertyAttributesEntry.getKey()).append(": ").append(propertyAttributesEntry.getValue());
            }
            sb.append("}");
            sb.append('}');
            return sb.toString();
        }

        public String getPrefixURI() {
            return prefixURI;
        }
    }

    private static List<RegistryEntry> registryEntries = new ArrayList<>();

    static {
        try {
            defaultRegisryURL = new URL("http://www.w3.org/ns/md");
        } catch (MalformedURLException e) {
            logger.error("Caught Exception", e); //To change body of catch statement use File | Settings | File Templates.
            throw new Error(e);
        }
    }


    public MicrodataRegistry()  {
        try {
            initRegistry(defaultRegisryURL);
        } catch (IOException e) {
            logger.error("Caught Exception", e); //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public MicrodataRegistry(URL url) throws IOException {
        initRegistry(url);
    }

    private void initRegistry(URL url) throws IOException {
        InputStream in = url.openStream();

        try {
            Map<String, Object> fromJson = (Map<String, Object>) jsonMapper.readValue(url, Object.class);
            for (Map.Entry<String, Object> js : fromJson.entrySet()) {
                RegistryEntry registryEntry = new RegistryEntry(js.getKey());
                Map<String, Object> entryValue = (Map<String, Object>) js.getValue();
                Map<String, Map<String, Object>> props = (Map<String, Map<String, Object>>) entryValue.get("properties");
                if (props != null) {
                    for (Map.Entry<String, Map<String, Object>> objectEntry : props.entrySet()) {
                        Map<String, Object> propertyAttributes = objectEntry.getValue();
                        registryEntry.addProperty(objectEntry.getKey(), new PropertyAttributes(propertyAttributes));
                    }
                }
                registryEntries.add(registryEntry);
            }
            if (logger.isInfoEnabled()) {
                for (RegistryEntry registryEntry : registryEntries) {
                    logger.info("registry entry {}", registryEntry);
                }
            }
            logger.debug("registry entries: {}", registryEntries);
        } finally {
            if(in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public RegistryEntry match( String urlString) {
        for (RegistryEntry entry : registryEntries) {
            if(urlString.startsWith(entry.prefixURI)) {
                return entry;
            }
        }
        return null;
    }
}
