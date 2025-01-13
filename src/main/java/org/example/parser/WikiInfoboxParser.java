package org.example.parser;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WikiInfoboxParser {
    private static final Logger logger = LoggerFactory.getLogger(WikiInfoboxParser.class);
    private static final Pattern INFOBOX_PATTERN = Pattern.compile("\\{\\{Pokémon Infobox([^}]*?)\\}\\}", Pattern.DOTALL);
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\|\\s*([^=]+?)\\s*=\\s*([^|\\}]*?)\\s*(?=\\||\\}\\})", Pattern.DOTALL);

    public Map<String, String> processWikitext(Map<String, String> data) {
        Map<String, String> result = new HashMap<>(data);
        
        if (data.containsKey("wikitext")) {
            Map<String, String> infoboxData = parseInfobox(data.get("wikitext"));
            result.putAll(infoboxData);
        }
        
        return result;
    }

    private Map<String, String> parseInfobox(String wikitext) {
        Map<String, String> parameters = new HashMap<>();
        
        // Find the infobox template
        Matcher infoboxMatcher = INFOBOX_PATTERN.matcher(wikitext);
        if (infoboxMatcher.find()) {
            String infoboxContent = infoboxMatcher.group(1);
            logger.debug("Found infobox content: \n" + infoboxContent);
            
            // Extract parameters from the infobox
            Matcher paramMatcher = PARAMETER_PATTERN.matcher(infoboxContent);
            while (paramMatcher.find()) {
                String key = paramMatcher.group(1).trim();
                String value = cleanWikiText(paramMatcher.group(2).trim());
                
                // Special handling for category field
                if (key.equals("category")) {
                    value = handleCategory(value);
                }
                
                if (!value.isEmpty()) {
                    parameters.put(key, value);
                }
            }
        } else {
            logger.warn("No Pokemon infobox found in the wikitext");
        }
        
        return parameters;
    }

    private String handleCategory(String value) {
        if (value.startsWith("{{tt|")) {
            // Extract category from template format {{tt|Category|Description}}
            int pipeIndex = value.indexOf('|', 5);
            if (pipeIndex > 0) {
                return value.substring(5, pipeIndex);
            }
        }
        return value;
    }

    private String cleanWikiText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove wiki markup
        text = text.replaceAll("\\{\\{tt\\|([^|]+)\\|[^}]+\\}\\}", "$1"); // Handle {{tt|text|description}}
        text = text.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+)\\]\\]", "$1"); // Handle [[link|text]]
        text = text.replaceAll("'''?([^']+)'''?", "$1"); // Handle '''text'''
        text = text.replaceAll("''([^']+)''", "$1"); // Handle ''text''
        text = text.replaceAll("<!--.*?-->", ""); // Remove comments
        text = text.replaceAll("<br\\s*/?\\s*>", " "); // Handle line breaks
        text = text.replaceAll("\\s+", " "); // Normalize whitespace

        return text.trim();
    }
}
