package nl.praegus.fitnesse.responders;

import nl.praegus.fitnesse.responders.util.ClassFinder;

import fitnesse.FitNesseContext;
import fitnesse.http.Request;
import fitnesse.http.Response;
import fitnesse.http.SimpleResponse;
import fitnesse.responders.WikiPageResponder;
import fitnesse.testrunner.WikiTestPage;
import fitnesse.testsystems.ClassPath;
import fitnesse.testsystems.slim.HtmlTableScanner;
import fitnesse.testsystems.slim.Table;
import fitnesse.testsystems.slim.TableScanner;
import fitnesse.wiki.WikiPage;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Responder for use with autocomplete javascript.
 * Adds testrunner classpaths to classloader, finds all classes that are imported and lists scenario's available
 * Returns JSON containing all classes for the requested page with their public methods, including parameters (types) using reflection
 * Also returns any scenario's on the testPage with their paramaters (names)
 */

public class AutoCompleteResponder extends WikiPageResponder {

    private static final Pattern ARG_PATTERN = Pattern.compile("@\\{(.+?)}");
    private static final Pattern OUT_PATTERN = Pattern.compile("\\$(.+?)=");
    private static final Pattern UNDERSCORE_PATTERN = Pattern.compile("\\W_(?=\\W|$)");
    private static final Set<String> METHODS_TO_IGNORE;

    static {
        METHODS_TO_IGNORE = new HashSet<>();
        METHODS_TO_IGNORE.add("toString");
        METHODS_TO_IGNORE.add("aroundSlimInvoke");
        METHODS_TO_IGNORE.add("getClass");
        METHODS_TO_IGNORE.add("equals");
        METHODS_TO_IGNORE.add("notify");
        METHODS_TO_IGNORE.add("notifyAll");
        METHODS_TO_IGNORE.add("wait");
        METHODS_TO_IGNORE.add("hashCode");
    }

    private JSONObject json = new JSONObject();
    private JSONArray classes = new JSONArray();
    private JSONArray scenarios = new JSONArray();
    private Set<String> packages = new HashSet<>();
    private JSONArray variables = new JSONArray();
    private WikiPage page;
    private FitNesseContext context;
    private URLClassLoader classLoader;
    private Map<String, Table> tableTemplateTables = new HashMap<>();


    @Override
    public Response makeResponse(FitNesseContext pagecontext, Request request) throws Exception {
        context = pagecontext;
        page = loadPage(context, request.getResource(), request.getMap());
        setClassPathsForPage();
        getAutoCompleteDataFromPage();

        SimpleResponse response = new SimpleResponse();
        response.setMaxAge(0);
        response.setStatus(200);
        response.setContentType("application/json");
        response.setContent(json.toString(3));

        return response;

    }

    private void getAutoCompleteDataFromPage() {
        TableScanner scanner = new HtmlTableScanner(makeHtml(context, page));
        for (int i = 0; i < scanner.getTableCount(); i++) {
            Table t = scanner.getTable(i);
            switch (t.getCellContents(0, 0).toLowerCase()) {
                case "import":
                    addPackage(t);
                    break;
                case "scenario":
                    addScenario(t);
                    break;
                case "table template":
                    addTableTemplate(t);
                    break;
                default:
                    //Skip!
                    break;
            }
            addVariables(t);
        }

        addClassesToAutocompleteList();
        json.put("classes", classes);
        json.put("scenarios", scenarios);
        json.put("variables", variables);

    }

    private void addVariables(Table t) {
        int numRows = t.getRowCount();
        for (int row = 0; row < numRows; row++) {
            if(t.getCellContents(0, row).matches("\\$\\S+=")){
                String varName = t.getCellContents(0, row).substring(0, t.getCellContents(0, row).length() -1);
                List<String> cells = new ArrayList<>();
                for (int c=0; c < t.getColumnCountInRow(row); c++ ) {
                    cells.add(t.getCellContents(c, row));
                }
                JSONObject varData = new JSONObject();
                varData.put("varName", varName);
                varData.put("html", varDefinitionTable(cells));
                varData.put("fullTable", tableToHtml(t));
                variables.put(varData);
            }
        }
    }

    private String varDefinitionTable(List<String> cells) {
        StringBuilder result = new StringBuilder();
        result.append("<table><tr>");
        for(String cell : cells) {
            result.append("<td>")
                  .append(cell)
                  .append("</td>");
        }
        result.append("</tr></table>");
        return result.toString();
    }

    private void addClassesToAutocompleteList() {
        for (String pkg : packages) {
            Set<Class> classList = new HashSet<>();

            try {
                classList.addAll(ClassFinder.getClasses(pkg, false, classLoader));
            } catch (Exception e) {
                System.err.println("Exception for package: " + pkg + " - " + e.getMessage());
            }

            for (Class klass : classList) {
                JSONObject thisClass = new JSONObject();
                thisClass.put("qualifiedName", klass.getName());
                thisClass.put("readableName", splitCamelCase(klass.getSimpleName()));
                thisClass.put("availableMethods", getMethods(klass));
                classes.put(thisClass);
            }
        }
    }

    private JSONArray getMethods(Class klass) {
        JSONArray cMethods = new JSONArray();
        try {
            Method[] methods = klass.getMethods();
            for (Method method : methods) {

                // If method is in the ignore list and not overridden in the current class, ignore it
                if (!METHODS_TO_IGNORE.contains(method.getName()) || method.getDeclaringClass().equals(klass)) {

                    String readableMethodName = splitCamelCase(method.getName());
                    StringBuilder insertText = new StringBuilder();
                    JSONObject thisMethod = new JSONObject();
                    thisMethod.put("name", readableMethodName);
                    Class<?>[] parameters = method.getParameterTypes();
                    int numberOfParams = parameters.length;
                    if (numberOfParams > 0) {
                        JSONArray params = new JSONArray();
                        for (Class<?> param : parameters) {
                            params.put(param.getSimpleName());
                        }
                        thisMethod.put("parameters", params);
                    }

                    String[] methodNameParts = readableMethodName.split(" ");
                    int numberOfParts = methodNameParts.length;

                    if (numberOfParams > numberOfParts) {
                        insertText.append(readableMethodName)
                                .append(" | ");
                        for (Class<?> param : parameters) {
                            insertText.append(param.getSimpleName())
                                    .append(", ");
                        }
                        insertText.append(" |");
                    } else {
                        int totalCells = numberOfParts + numberOfParams;

                        List<Integer> paramPositions = new ArrayList<>();
                        int paramPosition = totalCells - 1;

                        int n = 0;
                        while (n < numberOfParams) {
                            paramPositions.add(paramPosition);
                            paramPosition -= 2;
                            n++;
                        }
                        int prm = 0;
                        for (int p = 0; p < totalCells; p++) {
                            if (!paramPositions.contains(p)) {
                                insertText.append(methodNameParts[p - prm])
                                        .append(" ");
                            } else {
                                insertText.append("| ")
                                        .append(parameters[prm].getSimpleName())
                                        .append(" | ");
                                prm++;
                            }
                        }
                        if (numberOfParams == 0) {
                            insertText.append("|");
                        }
                    }

                    thisMethod.put("wikiText", insertText.toString());
                    cMethods.put(thisMethod);
                }
            }
        } catch (NoClassDefFoundError err) {
            //intentionally ignore classes that cannot be found
        }
        return cMethods;
    }

    private void addPackage(Table t) {
        for (int row = 1; row < t.getRowCount(); row++) {
            packages.add(t.getCellContents(0, row));
        }
    }

    private void addScenario(Table t) {
        StringBuilder scenarioName = new StringBuilder();
        StringBuilder insertText = new StringBuilder("|");
        JSONObject thisScenario = new JSONObject();
        JSONArray parameters = new JSONArray();

        if(UNDERSCORE_PATTERN.matcher(t.getCellContents(1, 0)).find()) {
            String textForAutocomplete = t.getCellContents(1, 0);
            String[] params = t.getCellContents(2, 0).split(",\\s*");
            for (String param : params) {
                parameters.put(param);
                textForAutocomplete = textForAutocomplete
                        .replaceFirst(UNDERSCORE_PATTERN.pattern(), " | " + param + " |");
            }
            if(!textForAutocomplete.endsWith("|")) {
                textForAutocomplete += " |";
            }
            insertText.append(" ").append(textForAutocomplete);

            String readableName = t.getCellContents(1, 0)
                    .replaceAll(UNDERSCORE_PATTERN.pattern(), " ");
            scenarioName.append(readableName);

        } else {
            for (int col = 1; col < t.getColumnCountInRow(0); col++) {
                insertText.append(" ")
                        .append(t.getCellContents(col, 0))
                        .append(" |");
                if ((col % 2) != 0) {
                    scenarioName.append(t.getCellContents(col, 0))
                            .append(" ");
                } else {
                    parameters.put(t.getCellContents(col, 0));
                }
            }
        }

        thisScenario.put("name", scenarioName.toString());
        thisScenario.put("wikiText", insertText.substring(2));
        thisScenario.put("insertText", insertText.toString());
        thisScenario.put("parameters", parameters);
        thisScenario.put("html", tableToHtml(t));
        scenarios.put(thisScenario);
    }

    private void addTableTemplate(Table t) {

        StringBuilder insertText = new StringBuilder("|");
        JSONObject thisScenario = new JSONObject();
        JSONArray parameters = new JSONArray();

        Set<String> inputs = new LinkedHashSet<>();
        Set<String> outputs= new LinkedHashSet<>();


        String tplName = t.getCellContents(1, 0);
        insertText.append(" ")
                .append(tplName)
                .append(" |");

        addAllMatchesFromTable(ARG_PATTERN, inputs, t);
        addAllMatchesFromTable(OUT_PATTERN, outputs, t);

        if(inputs.size() > 0 || outputs.size() > 0) {
            insertText.append("\r\n" + "|");
            for (String input : inputs) {
                parameters.put(input);
                insertText.append(input).append("|");
            }
            for (String output : outputs) {
                parameters.put(output);
                insertText.append(output).append("?").append("|");
            }
        }

        thisScenario.put("name", tplName);
        thisScenario.put("wikiText", insertText.substring(2));
        thisScenario.put("insertText", insertText.toString());
        thisScenario.put("parameters", parameters);
        thisScenario.put("html", tableToHtml(t));
        scenarios.put(thisScenario);
        tableTemplateTables.put(tplName, t);
    }

    private void addAllMatchesFromTable(Pattern pattern, Set<String> found, Table t) {

        for (int row = 1; row < t.getRowCount(); row++) {

            StringBuilder potentialTableTemplateName = new StringBuilder();

            for (int col = 0; col < t.getColumnCountInRow(row); col++) {

                if ((col % 2) == 0) {
                    potentialTableTemplateName.append(t.getCellContents(col, row))
                            .append(" ");
                }

                String cellContent = t.getCellContents(col, row);
                Matcher m = pattern.matcher(cellContent);
                while (m.find()) {
                    String input = m.group(1);
                    found.add(input);
                }
            }

            String cleanPotentialTemplateName = potentialTableTemplateName.toString().trim().replaceAll(";$", "");
            if (tableTemplateTables.containsKey(cleanPotentialTemplateName)) {
                addAllMatchesFromTable(pattern, found, tableTemplateTables.get(cleanPotentialTemplateName));
            }
        }
    }

    private String tableToHtml(Table t) {
        int numRows = t.getRowCount();
        int maxCols = 0;
        StringBuilder html = new StringBuilder("<table>");
        for (int row = 0; row < numRows; row++) {
            int rowCols = t.getColumnCountInRow(row);
            if (rowCols > maxCols) {
                maxCols = rowCols;
            }
        }
        for (int row = 0; row < numRows; row++) {
            int rowCols = t.getColumnCountInRow(row);
            int lastCol = rowCols - 1;

            html.append("<tr>");
            for (int col = 0; col < rowCols; col++) {
                if (col == lastCol && col < (maxCols - 1)) {
                    html.append("<td colspan=")
                            .append(maxCols - col).append(">")
                            .append(t.getCellContents(col, row))
                            .append("</td>");
                } else {
                    html.append("<td>")
                            .append(t.getCellContents(col, row)
                            ).append("</td>");
                }
            }
            html.append("</tr>");
        }
        html.append("</table>");

        return html.toString();
    }


    private void setClassPathsForPage() {
        WikiTestPage testPage = new WikiTestPage(page);
        ClassPath classPath = testPage.getClassPath();

        URL[] urls = new URL[classPath.getElements().size()];

        int i = 0;
        for (String path : classPath.getElements()) {
            File classPathItem = new File(path);
            try {
                urls[i] = classPathItem.toURI().toURL();
            } catch (MalformedURLException e) {
                //this shouldn't happen!
                e.printStackTrace();
            }
            i++;
        }

        addToClassPath(urls);
    }

    private void addToClassPath(URL[] urls) {
        try {
            classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        ).toLowerCase();
    }

}
