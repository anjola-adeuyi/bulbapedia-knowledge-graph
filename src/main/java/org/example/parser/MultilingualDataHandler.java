package org.example.parser;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class MultilingualDataHandler {
    private static final Logger logger = LoggerFactory.getLogger(MultilingualDataHandler.class);
    private static final String TSV_FILE = "pokedex-i18n.tsv";
    private final Map<String, Map<String, String>> pokemonLabels = new HashMap<>();
    private final Set<String> supportedLanguages = new HashSet<>();

    public void loadTSVData() {
        try {
            // First try to read from the classpath
            String content = new String(getClass().getResourceAsStream("/pokedex-i18n.tsv").readAllBytes());
            processContent(content);
        } catch (Exception e) {
            // If not found in classpath, try as a regular file
            try (BufferedReader reader = new BufferedReader(new FileReader(TSV_FILE))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                processContent(content.toString());
            } catch (IOException ex) {
                logger.error("Error loading multilingual data:", ex);
            }
        }
    }

    private void processContent(String content) {
        String[] lines = content.split("\n");
        if (lines.length > 0) {
            // Extract language codes from header
            String[] headers = lines[0].split("\t");
            for (int i = 1; i < headers.length; i++) {
                supportedLanguages.add(headers[i].trim());
            }

            // Process each line
            for (int i = 1; i < lines.length; i++) {
                processLine(lines[i]);
            }
            logger.info("Loaded multilingual data for {} Pokemon in {} languages", 
                       pokemonLabels.size(), supportedLanguages.size());
            logger.debug("Supported languages: {}", supportedLanguages);
        }
    }

    private void processLine(String line) {
        String[] parts = line.split("\t");
        if (parts.length > 1) {
            String pokemonId = parts[0].trim();
            Map<String, String> labels = new HashMap<>();
            
            for (int i = 1; i < parts.length && i <= supportedLanguages.size(); i++) {
                String lang = new ArrayList<>(supportedLanguages).get(i-1);
                String label = parts[i].trim();
                if (!label.isEmpty()) {
                    labels.put(lang, label);
                }
            }
            
            pokemonLabels.put(pokemonId, labels);
        }
    }

    public void enrichModelWithLabels(Model model) {
        logger.info("Starting model enrichment with multilingual labels");
        boolean hasJapanese = false;
        boolean hasFrench = false;
        
        // First add base English labels from schema:name
        ResIterator nameIterator = model.listResourcesWithProperty(
            model.createProperty("http://schema.org/name"));
            
        while (nameIterator.hasNext()) {
            Resource pokemon = nameIterator.next();
            String name = pokemon.getProperty(
                model.createProperty("http://schema.org/name")).getString();
            String identifier = pokemon.getProperty(
                model.createProperty("http://schema.org/identifier")).getString();
            
            // Add English label
            pokemon.addProperty(RDFS.label, model.createLiteral(name, "en"));
            
            // Add Japanese label (from wiki data)
            Statement jnameStmt = pokemon.getProperty(
                model.createProperty("http://example.org/pokemon/japaneseName"));
            if (jnameStmt != null) {
                pokemon.addProperty(RDFS.label, 
                    model.createLiteral(jnameStmt.getString(), "ja"));
                hasJapanese = true;
            }
            
            // Add French label (from pokedex-i18n.tsv)
            Map<String, String> translations = pokemonLabels.get(identifier);
            if (translations != null && translations.containsKey("fr")) {
                pokemon.addProperty(RDFS.label,
                    model.createLiteral(translations.get("fr"), "fr"));
                hasFrench = true;
            }
        }
        
        logger.info("Added labels in languages - English: true, Japanese: {}, French: {}", 
            hasJapanese, hasFrench);

        // Add translations from TSV file
        ResIterator pokemonIterator = model.listResourcesWithProperty(
            model.createProperty("http://schema.org/identifier"));
            
        while (pokemonIterator.hasNext()) {
            Resource pokemon = pokemonIterator.next();
            String identifier = pokemon.getProperty(
                model.createProperty("http://schema.org/identifier"))
                .getString();
                
            // Add labels for this Pokemon in all available languages
            Map<String, String> labels = pokemonLabels.get(identifier);
            if (labels != null) {
                for (Map.Entry<String, String> entry : labels.entrySet()) {
                    pokemon.addProperty(RDFS.label, 
                        model.createLiteral(entry.getValue(), entry.getKey()));
                }
            }
        }
        
        logger.info("Enriched model with multilingual labels");
    }

    public Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(supportedLanguages);
    }
}
