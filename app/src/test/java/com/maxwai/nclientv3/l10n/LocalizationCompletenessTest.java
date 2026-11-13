package com.maxwai.nclientv3.l10n;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 4.6.0 guard for the Simplified Chinese / English localisation: every translatable string in
 * values/ must exist in values-zh-rCN, and the English defaults in values/ must not contain
 * Chinese (which would mean a Chinese literal was moved into the wrong file).
 * <p>
 * Deliberately not Lint's MissingTranslation, which would also demand the other eleven locales.
 */
public class LocalizationCompletenessTest {

    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u9fff\\uf900-\\ufaff\\u3000-\\u303f\\uff00-\\uffef]");
    private static final Pattern LETTERS = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXc%]");

    private static File resDir(String name) {
        // Unit tests run with the module directory (app/) as working directory.
        File f = new File("src/main/res/" + name);
        if (!f.isDirectory()) f = new File("app/src/main/res/" + name);
        assertTrue("resource folder not found: " + f.getAbsolutePath(), f.isDirectory());
        return f;
    }

    /** name -> text of every &lt;string&gt; in the folder; translatable="false" ones are skipped. */
    private static Map<String, String> strings(String folder, boolean skipUntranslatable) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        File[] files = resDir(folder).listFiles((d, n) -> n.endsWith(".xml"));
        assertTrue(files != null);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        for (File file : files) {
            Document doc = f.newDocumentBuilder().parse(file);
            NodeList list = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node n = list.item(i);
                if (!(n instanceof Element) || !"string".equals(n.getNodeName())) continue;
                Element e = (Element) n;
                if (skipUntranslatable && "false".equals(e.getAttribute("translatable"))) continue;
                out.put(e.getAttribute("name"), e.getTextContent());
            }
        }
        return out;
    }

    /** Text the user actually reads in values/: not a reference, has words. */
    private static boolean needsTranslation(String value) {
        String v = value.trim();
        if (v.startsWith("@")) return false;
        v = FORMAT.matcher(v).replaceAll("").replace("\\n", "");
        return LETTERS.matcher(v).find() || CJK.matcher(v).find();
    }

    @Test
    public void everyDefaultStringHasSimplifiedChinese() throws Exception {
        Map<String, String> en = strings("values", true);
        Map<String, String> zh = strings("values-zh-rCN", false);
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : en.entrySet()) {
            if (needsTranslation(e.getValue()) && !zh.containsKey(e.getKey())) missing.add(e.getKey());
        }
        assertTrue("values-zh-rCN is missing " + missing.size() + " key(s): " + missing, missing.isEmpty());
    }

    @Test
    public void mergeStringsHaveTraditionalChinese() throws Exception {
        Map<String, String> zh = strings("values-zh-rTW", false);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new File(resDir("values"), "strings_472.xml"));
        NodeList nodes = document.getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); i++) {
            String name = ((Element) nodes.item(i)).getAttribute("name");
            assertTrue("values-zh-rTW missing " + name, zh.containsKey(name));
        }
    }

    @Test
    public void englishDefaultsContainNoChinese() throws Exception {
        Map<String, String> en = strings("values", false);
        List<String> cjk = new ArrayList<>();
        for (Map.Entry<String, String> e : en.entrySet()) {
            if (CJK.matcher(e.getValue()).find()) cjk.add(e.getKey());
        }
        assertTrue("values/ contains Chinese text in: " + cjk, cjk.isEmpty());
    }
}
