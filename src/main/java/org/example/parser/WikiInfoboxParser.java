package org.example.parser;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiInfoboxParser {
    private static final Logger logger = LoggerFactory.getLogger(WikiInfoboxParser.class);
    private static final Pattern INFOBOX_PATTERN = Pattern.compile("\\{\\{Pokémon Infobox([^}]*?)\\}\\}", Pattern.DOTALL);
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\|\\s*([^=]+?)\\s*=\\s*([^|\\}]*?)\\s*(?=\\||\\}\\})", Pattern.DOTALL);

    public Map<String, String> parseInfobox(String wikitext) {
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
                parameters.put(key, value);
            }
        } else {
            logger.warn("No Pokemon infobox found in the wikitext");
        }
        
        return parameters;
    }

    private String cleanWikiText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove wiki markup like [[]] and ''
        text = text.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+)\\]\\]", "$1");
        text = text.replaceAll("'''?([^']+)'''?", "$1");
        text = text.replaceAll("''([^']+)''", "$1");
        // Remove HTML comments
        text = text.replaceAll("<!--.*?-->", "");
        // Remove <br> tags
        text = text.replaceAll("<br\\s*/?\\s*>", " ");
        // Remove multiple spaces
        text = text.replaceAll("\\s+", " ");
        return text.trim();
    }

    public Map<String, String> extractPokemonInfo(JSONObject apiResponse) {
        if (!apiResponse.has("parse")) {
            logger.warn("API response does not contain 'parse' object");
            return Map.of();
        }

        JSONObject parseData = apiResponse.getJSONObject("parse");
        String wikitext = parseData.getJSONObject("wikitext").getString("*");
        
        Map<String, String> infobox = parseInfobox(wikitext);

        // Add page metadata
        infobox.put("pageid", String.valueOf(parseData.getInt("pageid")));
        infobox.put("title", parseData.getString("title"));
        
        return infobox;
    }
}
