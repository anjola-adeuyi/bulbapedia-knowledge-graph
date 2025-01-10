package org.example.parser;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiInfoboxParser {
    private static final Pattern INFOBOX_PATTERN = Pattern.compile("\\{\\{(Pokémon Infobox|Infobox)[^}]*\\}\\}", Pattern.DOTALL);
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\|\\s*([^=]+)\\s*=\\s*([^|\\}]+)");

    public Map<String, String> parseInfobox(String wikitext) {
        Map<String, String> parameters = new HashMap<>();
        
        // Find the infobox template
        Matcher infoboxMatcher = INFOBOX_PATTERN.matcher(wikitext);
        if (infoboxMatcher.find()) {
            String infoboxContent = infoboxMatcher.group(0);
            
            // Extract parameters from the infobox
            Matcher paramMatcher = PARAMETER_PATTERN.matcher(infoboxContent);
            while (paramMatcher.find()) {
                String key = paramMatcher.group(1).trim();
                String value = cleanWikiText(paramMatcher.group(2).trim());
                parameters.put(key, value);
            }
        }
        
        return parameters;
    }

    private String cleanWikiText(String text) {
        // Remove wiki markup like [[]] and ''
        text = text.replaceAll("\\[\\[([^\\]|]*\\|)?([^\\]]+)\\]\\]", "$2");
        text = text.replaceAll("'''?([^']+)'''?", "$1");
        text = text.replaceAll("''([^']+)''", "$1");
        // Remove HTML comments
        text = text.replaceAll("<!--.*?-->", "");
        // Remove <br> tags
        text = text.replaceAll("<br\\s*/>", " ");
        return text.trim();
    }

    public Map<String, String> extractPokemonInfo(JSONObject apiResponse) {
        if (!apiResponse.has("parse")) {
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
