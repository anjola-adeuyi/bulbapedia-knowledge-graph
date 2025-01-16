package org.example.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiInfoboxParser {
    private static final Logger logger = LoggerFactory.getLogger(WikiInfoboxParser.class);
    
    private static final Map<String, Pattern> INFOBOX_PATTERNS = new HashMap<>();
    static {
        INFOBOX_PATTERNS.put("Pokemon", Pattern.compile("\\{\\{Pokémon Infobox([^}]*?)\\}\\}", Pattern.DOTALL));
        INFOBOX_PATTERNS.put("Move", Pattern.compile("\\{\\{Move Infobox([^}]*?)\\}\\}", Pattern.DOTALL));
        INFOBOX_PATTERNS.put("Ability", Pattern.compile("\\{\\{Ability Infobox([^}]*?)\\}\\}", Pattern.DOTALL));
        INFOBOX_PATTERNS.put("Item", Pattern.compile("\\{\\{Item Infobox([^}]*?)\\}\\}", Pattern.DOTALL));
        INFOBOX_PATTERNS.put("Location", Pattern.compile("\\{\\{Location Infobox([^}]*?)\\}\\}", Pattern.DOTALL));
    }
    
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\|\\s*([^=]+?)\\s*=\\s*([^|\\}]*?)\\s*(?=\\||\\}\\})", Pattern.DOTALL);
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("\\[\\[Category:([^\\]]+)\\]\\]");

    public Map<String, String> processWikitext(Map<String, String> data) {
        Map<String, String> result = new HashMap<>(data);
        
        if (data.containsKey("wikitext")) {
            String wikitext = data.get("wikitext");
            
            // Process all infobox types
            for (Map.Entry<String, Pattern> entry : INFOBOX_PATTERNS.entrySet()) {
                String type = entry.getKey();
                Pattern pattern = entry.getValue();
                Matcher matcher = pattern.matcher(wikitext);
                
                if (matcher.find()) {
                    Map<String, String> infoboxData = parseInfobox(matcher.group(1), type);
                    result.putAll(infoboxData);
                    result.put("infoboxType", type);
                    break;  // Only process the first matching infobox
                }
            }
            
            // Process categories
            Set<String> categories = new HashSet<>();
            Matcher categoryMatcher = CATEGORY_PATTERN.matcher(wikitext);
            while (categoryMatcher.find()) {
                categories.add(categoryMatcher.group(1).trim());
            }
            if (!categories.isEmpty()) {
                result.put("categories", String.join("|", categories));
            }
        }
        
        return result;
    }

    private Map<String, String> parseInfobox(String infoboxContent, String type) {
        Map<String, String> parameters = new HashMap<>();
        Matcher paramMatcher = PARAMETER_PATTERN.matcher(infoboxContent);
        
        while (paramMatcher.find()) {
            String key = paramMatcher.group(1).trim();
            String value = cleanWikiText(paramMatcher.group(2).trim());
            
            // Special handling for different infobox types
            switch (type) {
                case "Pokemon":
                    handlePokemonParameter(parameters, key, value);
                    break;
                case "Move":
                    handleMoveParameter(parameters, key, value);
                    break;
                case "Ability":
                    handleAbilityParameter(parameters, key, value);
                    break;
                case "Item":
                    handleItemParameter(parameters, key, value);
                    break;
                case "Location":
                    handleLocationParameter(parameters, key, value);
                    break;
            }
        }
        
        return parameters;
    }

    private void handlePokemonParameter(Map<String, String> params, String key, String value) {
        switch (key) {
            case "ndex":
            case "type1":
            case "type2":
            case "ability1":
            case "ability2":
            case "height-m":
            case "weight-kg":
            case "category":
            case "name":
            case "jname":
            case "tmname":
                params.put(key, value);
                break;
            case "generation":
                try {
                    int gen = extractGeneration(value);
                    params.put("generation", String.valueOf(gen));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid generation value: {}", value);
                }
                break;
        }
    }

    private void handleMoveParameter(Map<String, String> params, String key, String value) {
        switch (key) {
            case "name":
            case "type":
            case "category":
            case "power":
            case "accuracy":
            case "pp":
            case "description":
                params.put("move_" + key, value);
                break;
        }
    }

    private void handleAbilityParameter(Map<String, String> params, String key, String value) {
        switch (key) {
            case "name":
            case "jname":
            case "effect":
            case "description":
                params.put("ability_" + key, value);
                break;
        }
    }

    private void handleItemParameter(Map<String, String> params, String key, String value) {
        switch (key) {
            case "name":
            case "type":
            case "effect":
            case "description":
                params.put("item_" + key, value);
                break;
        }
    }

    private void handleLocationParameter(Map<String, String> params, String key, String value) {
        switch (key) {
            case "name":
            case "region":
            case "type":
            case "description":
                params.put("location_" + key, value);
                break;
        }
    }

    private String cleanWikiText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove wiki markup
        text = text.replaceAll("\\{\\{tt\\|([^|]+)\\|[^}]+\\}\\}", "$1");  // Handle {{tt|text|description}}
        text = text.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+)\\]\\]", "$1");  // Handle [[link|text]]
        text = text.replaceAll("'''?([^']+)'''?", "$1");  // Handle '''text'''
        text = text.replaceAll("''([^']+)''", "$1");  // Handle ''text''
        text = text.replaceAll("<!--.*?-->", "");  // Remove comments
        text = text.replaceAll("<br\\s*/?\\s*>", " ");  // Handle line breaks
        text = text.replaceAll("\\{\\{[^}]+\\}\\}", "");  // Remove remaining templates
        text = text.replaceAll("\\s+", " ");  // Normalize whitespace

        return text.trim();
    }

    private int extractGeneration(String value) {
        // Try to extract generation number from various formats
        Pattern genPattern = Pattern.compile("(\\d+)");
        Matcher genMatcher = genPattern.matcher(value);
        if (genMatcher.find()) {
            return Integer.parseInt(genMatcher.group(1));
        }
        throw new NumberFormatException("Could not extract generation number from: " + value);
    }
}
