package it.olly.templater.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class IndexController {

    private static final String INDEX_HTML = "/web/index.html";
    private static String INDEX_CONTENT;

    public static final String BASE_TEMPLATE_FILE = "/templates/main.txt";

    @PostConstruct
    private void init() {
        InputStream in = getClass().getResourceAsStream(INDEX_HTML);
        try {
            INDEX_CONTENT = IOUtils.toString(in, Charset.forName("utf-8"));
            in.close();
        } catch (IOException e) {
        }
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    protected String doGet() throws ServletException, IOException {
        return INDEX_CONTENT;
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    protected String doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String template = null;
        try (InputStream in = getClass().getResourceAsStream(BASE_TEMPLATE_FILE)) {
            template = IOUtils.toString(in, Charset.forName("utf-8"));
        }

        boolean man = StringUtils.isNotBlank(req.getParameter("man"));
        boolean old = StringUtils.isNotBlank(req.getParameter("old"));
        String str = req.getParameter("str");

        String params = "man=" + man + "\n" + "old=" + old + "\n" + "str=" + str + "\n";

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("man", man);
        placeholders.put("old", old);
        placeholders.put("${str}", str);
        String after = parseTemplateWithPlaceholders(BASE_TEMPLATE_FILE, placeholders);

        return "*** params ***\n" + params + "\n\n*** template ***\n" + template + "\n\n*** parsed ***\n" + after;
    }

    private String parseTemplateWithPlaceholders(String templateName, Map<String, Object> placeholders)
            throws IOException {
        try (InputStream in = getClass().getResourceAsStream(templateName)) {
            String content = IOUtils.toString(in, Charset.forName("utf-8"));

            while (content != null && content.contains("${")) {
                boolean replaced = false;
                // replace simple placeholders
                for (String replace : placeholders.keySet()) {
                    if (replace.startsWith("${")) { // it's a real placeholder
                        if (content.contains(replace)) {
                            content = content.replace(replace, String.valueOf(placeholders.get(replace)));
                            replaced = true;
                        }
                    }
                }

                {
                    // check for if: placeholders
                    String ifRegex = "\\$\\{if:([^}]*)\\}([\\s\\S]*?)\\$\\{\\/if:\\1\\}";
                    Matcher m = Pattern.compile(ifRegex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
                            .matcher(content);
                    while (m.find()) {
                        String found = m.group(); // the whole string
                        String clause = m.group(1); // the if clause
                        String internalCode = m.group(2);
                        boolean clauseVerified = false;
                        if (clause.startsWith("!")) { // check if the if clause has a negation
                            clause = clause.substring(1)
                                    .trim();
                            clauseVerified = !Boolean.valueOf(String.valueOf(placeholders.get(clause)));
                        } else {
                            clauseVerified = Boolean.valueOf(String.valueOf(placeholders.get(clause)));
                        }
                        if (clauseVerified) {
                            content = content.replace(found, internalCode);
                        } else {
                            content = content.replace(found, "");
                        }
                        replaced = true;
                    }
                }

                {
                    // check for file: placeholders
                    String fileRegex = "\\$\\{file:[^}]*\\}";
                    Matcher m = Pattern.compile(fileRegex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
                            .matcher(content);
                    while (m.find()) {
                        String found = m.group();
                        String filename = found.substring(7, found.length() - 1);
                        // load content
                        InputStream importing = getClass().getResourceAsStream(filename);
                        String toReplace = IOUtils.toString(importing, Charset.forName("utf-8"));
                        content = content.replace(found, toReplace);
                        replaced = true;
                    }
                }

                if (!replaced) {
                    throw new IOException("there's something i cannot replace parsing some placeholder: " + content);
                }
            }
            return content;
        }
    }

}