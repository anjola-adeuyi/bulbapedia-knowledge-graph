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
        text = text.replaceAll("<!--.*?-->", "");
        text = text.replaceAll("<br\\s*/?\\s*>", " ");
        text = text.replaceAll("\\s+", " ");
        return text.trim();
    }
}
