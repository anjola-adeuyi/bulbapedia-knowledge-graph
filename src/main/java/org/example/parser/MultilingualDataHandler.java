package org.example.parser;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MultilingualDataHandler {
    private static final Logger logger = LoggerFactory.getLogger(MultilingualDataHandler.class);
    private static final String TSV_FILE = "pokedex-i18n.tsv";
    private final Map<String, Map<String, String>> pokemonLabels = new HashMap<>();
    private final Set<String> supportedLanguages = new HashSet<>();

    private void processContent(String content) {
        String[] lines = content.split("\n");
        if (lines.length > 0) {
            String[] headers = lines[0].split("\t");
            for (int i = 1; i < headers.length; i++) {
                supportedLanguages.add(headers[i].trim());
            }

            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].split("\t");
                if (parts.length > 1) {
                    String pokemonId = parts[0].trim();
                    Map<String, String> labels = new HashMap<>();
                    for (int j = 1; j < parts.length && j < headers.length; j++) {
                        String lang = headers[j].trim();
                        if (j < parts.length && !parts[j].trim().isEmpty()) {
                            labels.put(lang, parts[j].trim());
                        }
                    }
                    pokemonLabels.put(pokemonId, labels);
                }
            }
        }
        logger.info("Loaded multilingual data for {} Pokemon in {} languages", 
            pokemonLabels.size(), supportedLanguages.size());
        logger.debug("Supported languages: {}", supportedLanguages);
    }

    public void enrichModelWithLabels(Model model) {
        logger.info("Starting model enrichment with multilingual labels");
        Set<String> addedLanguages = new HashSet<>();
        
        ResIterator resIterator = model.listResourcesWithProperty(
            model.createProperty("http://schema.org/identifier"));
            
        while (resIterator.hasNext()) {
            Resource pokemon = resIterator.next();
            
            // Add English label from name
            String name = pokemon.getProperty(
                model.createProperty("http://schema.org/name"))
                .getString();
            pokemon.addProperty(RDFS.label, model.createLiteral(name, "en"));
            addedLanguages.add("en");
            
            // Add Japanese label from property
            Statement jnameStmt = pokemon.getProperty(
                model.createProperty("http://example.org/pokemon/japaneseName"));
            if (jnameStmt != null) {
                pokemon.addProperty(RDFS.label, 
                    model.createLiteral(jnameStmt.getString(), "ja"));
                addedLanguages.add("ja");
            }
            
            // Add romaji name
            Statement romajiStmt = pokemon.getProperty(
                model.createProperty("http://example.org/pokemon/romajiName"));
            if (romajiStmt != null) {
                pokemon.addProperty(RDFS.label,
                    model.createLiteral(romajiStmt.getString(), "ja-Latn"));
                addedLanguages.add("ja-Latn");
            }
            
            // Add other languages from TSV
            String identifier = pokemon.getProperty(
                model.createProperty("http://schema.org/identifier"))
                .getString();
            
            Map<String, String> translations = pokemonLabels.get(identifier);
            if (translations != null) {
                for (Map.Entry<String, String> entry : translations.entrySet()) {
                    pokemon.addProperty(RDFS.label, 
                        model.createLiteral(entry.getValue(), entry.getKey()));
                    addedLanguages.add(entry.getKey());
                }
            }
        }
        
        logger.info("Added labels in the following languages: {}", addedLanguages);
        logger.info("Enriched model with multilingual labels");
    }

    public void loadTSVData() {
        try {
            // First try to read from classpath
            InputStream inStream = getClass().getClassLoader()
                .getResourceAsStream(TSV_FILE);
            if (inStream != null) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inStream, StandardCharsets.UTF_8))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    processContent(content.toString());
                }
            } else {
                // Try as regular file
                try (BufferedReader reader = new BufferedReader(
                    new FileReader(TSV_FILE, StandardCharsets.UTF_8))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    processContent(content.toString());
                }
            }
        } catch (IOException e) {
            logger.error("Error loading multilingual data:", e);
        }
    }
}
