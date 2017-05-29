package com.github.tcnh.fitnesse.responders;


import com.github.tcnh.fitnesse.responders.util.ClassFinder;
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

    private JSONObject json = new JSONObject();
    private JSONArray classes = new JSONArray();
    private JSONArray scenarios = new JSONArray();
    private List<String> packages = new ArrayList<>();
    private JSONArray variables = new JSONArray();
    private WikiPage page;
    private FitNesseContext context;


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
                default:
                    //Skip!
                    break;
            }
        }

        addClassesToAutocompleteList();
        getVariablesInScope();
        json.put("classes", classes);
        json.put("scenarios", scenarios);
        json.put("variables", variables);

    }

    private void getVariablesInScope() {
        String html = makeHtml(context, page);
        Matcher m = Pattern.compile("(\\$[^\\s]+)=").matcher(html);
        while (m.find()) {
            variables.put(m.group(1));
        }
    }

    private void addClassesToAutocompleteList() {
        for (String pkg : packages) {
            List<Class> classList = new ArrayList<>();

            try {
                classList.addAll(ClassFinder.getClasses(pkg, false));
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

    private JSONArray getMethods (Class klass) {
        JSONArray cMethods = new JSONArray();
        try{
            Method[] methods = klass.getMethods();
            for (Method method : methods) {
                String readableMethodName = splitCamelCase(method.getName());
                String insertText = "";
                JSONObject thisMethod = new JSONObject();
                thisMethod.put("name", readableMethodName);
                Class<?>[] parameters = method.getParameterTypes();
                int numberOfParams = parameters.length;
                if (numberOfParams > 0) {
                    JSONArray params = new JSONArray();
                    for(Class<?> param : parameters) {
                        params.put(param.getSimpleName());
                    }

                    thisMethod.put("parameters", params);
                }
                String[] methodNameParts = readableMethodName.split(" ");
                int numberOfParts = methodNameParts.length;
                if (numberOfParams > numberOfParts) {
                    insertText += readableMethodName + " | ";
                    for(Class<?> param : parameters) {
                        insertText += param.getSimpleName() + ", ";
                    }
                    insertText += " |";
                } else {
                    int totalCells = numberOfParts + numberOfParams;

                    List<Integer> paramPositions = new ArrayList<>();
                    int paramPosition = totalCells -1;

                    for(int n = 0; n < numberOfParams; n++){
                        paramPositions.add(paramPosition);
                        paramPosition -= 2;
                    }
                    int prm = 0;
                    for(int p = 0; p < totalCells; p++) {
                        if(!paramPositions.contains(p)){
                            insertText += methodNameParts[p - prm] + " ";
                        } else {
                            insertText += "| " + parameters[prm].getSimpleName() + " | ";
                            prm++;
                        }
                    }
                    if(numberOfParams == 0) {
                        insertText += "|";
                    }
                }

                thisMethod.put("wikiText", insertText);
                cMethods.put(thisMethod);
            }
        } catch (NoClassDefFoundError err) {
            System.err.println("NoClassDefFoundError: " + err.getMessage());
        }
        return cMethods;
    }


    private void addPackage(Table t) {
        for (int row = 1; row < t.getRowCount(); row++) {
            packages.add(t.getCellContents(0, row));
    }
    }

    private void addScenario(Table t) {

        String scenarioName = "";
        String insertText = "|";
        JSONObject thisScenario = new JSONObject();
        JSONArray parameters = new JSONArray();
       for (int col = 1; col < t.getColumnCountInRow(0); col++) {
            insertText += " " + t.getCellContents(col, 0) + " |";
            if ((col % 2) != 0) {
                scenarioName += t.getCellContents(col, 0) + " ";
            } else {
                parameters.put(t.getCellContents(col, 0));
            }
        }

        thisScenario.put("name", scenarioName);
        thisScenario.put("wikiText", insertText.substring(2));
        thisScenario.put("insertText", insertText);
        thisScenario.put("parameters", parameters);
        thisScenario.put("html", tableToHtml(t));
        scenarios.put(thisScenario);
    }

    private String tableToHtml(Table t) {
        int numRows = t.getRowCount();
        int maxCols = 0;
        String html = "<table>";
        for(int row = 0; row < numRows; row++) {
            int rowCols = t.getColumnCountInRow(row);
            if(rowCols > maxCols) {
                maxCols = rowCols;
            }
        }
        for(int row = 0; row < numRows; row++) {
            int rowCols = t.getColumnCountInRow(row);
            int lastCol = rowCols -1;

            html += "<tr>";
            for(int col = 0; col < rowCols; col++) {
                if (col == lastCol && col < (maxCols -1)) {
                    html += "<td colspan=" + (maxCols - col) + ">" + t.getCellContents(col, row) + "</td>";
                } else {
                    html += "<td>" + t.getCellContents(col, row) + "</td>";
                }
            }
            html += "</tr>";
        }
        html += "</table>";

        return html;
    }


    private void setClassPathsForPage() {
        WikiTestPage testPage = new WikiTestPage(page);
        ClassPath classPath = testPage.getClassPath();
        for (String path : classPath.getElements()) {
            File classPathItem = new File(path);
            addToClassPath(classPathItem);
        }
    }

    private static void addToClassPath(File file) {
        try {
            URL url = file.toURI().toURL();
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
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
