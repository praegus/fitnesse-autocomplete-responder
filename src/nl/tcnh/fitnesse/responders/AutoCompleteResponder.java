package nl.tcnh.fitnesse.responders;


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
import nl.tcnh.fitnesse.responders.util.ClassFinder;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

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
    private JSONObject classes = new JSONObject();
    private JSONArray scenarios = new JSONArray();
    private List<String> packages = new ArrayList<>();


    @Override
    public Response makeResponse(FitNesseContext context, Request request) throws Exception {

        WikiPage page = loadPage(context, request.getResource(), request.getMap());
        setClassPathsForPage(page);
        getAutoCompleteDataFromPage(context, page);

        SimpleResponse response = new SimpleResponse();
        response.setMaxAge(0);
        response.setStatus(404);
        response.setContentType("application/json");
        response.setContent(json.toString(3));

        return response;

    }


    private void getAutoCompleteDataFromPage(FitNesseContext context, WikiPage page) {
        TableScanner scanner = new HtmlTableScanner(makeHtml(context, page));
        for (int i = 0; i < scanner.getTableCount(); i++) {
            Table t = scanner.getTable(i);
            switch (t.getCellContents(0, 0).toLowerCase()) {
                case "import":
                    addPackage(t);
                    break;
                case "library":
                    addLibraryFixture(t);
                    break;
                case "scenario":
                    addScenario(t);
                    break;
                default:
                    //Skip!
                    break;
            }
        }

        addClassesToAutocopleteList();
        json.put("classes", classes);
        json.put("scenarios", scenarios);

    }

    private void addClassesToAutocopleteList() {
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
                classes.put(klass.getSimpleName(),thisClass);
            }
        }
    }

    private JSONArray getMethods (Class klass) {
        JSONArray cMethods = new JSONArray();
        try{
            Method[] methods = klass.getMethods();
            for (Method method : methods) {
                JSONObject thisMethod = new JSONObject();
                thisMethod.put("name", splitCamelCase(method.getName()));
                if (method.getParameters().length > 0) {
                    JSONArray params = new JSONArray();
                    for (Parameter param : method.getParameters()) {
                        params.put(param.getType().getSimpleName());
                    }
                    thisMethod.put("parameters", params);
                }
                cMethods.put(thisMethod);
            }
        } catch (NoClassDefFoundError err) {
            System.err.println("NoClassDefFoundError: " + err.getMessage());
        }
        return cMethods;
    }

    private void addLibraryFixture(Table t) {
        //elke rij (behalve header) toevoegen aan classes
        //TODO: Implement
    }

    private void addPackage(Table t) {
        for (int row = 1; row < t.getRowCount(); row++) {
            packages.add(t.getCellContents(0, row));
        }
    }

    private void addScenario(Table t) {
        String scenarioName = "";

        JSONObject thisScenario = new JSONObject();
        JSONArray parameters = new JSONArray();

        for (int col = 1; col < t.getColumnCountInRow(0); col++) {
            if ((col % 2) != 0) {
                scenarioName += t.getCellContents(col, 0) + " ";
            } else {
                parameters.put(t.getCellContents(col, 0));
            }
        }

        thisScenario.put("name", scenarioName);
        thisScenario.put("parameters", parameters);
        scenarios.put(thisScenario);
    }

    private void setClassPathsForPage(WikiPage page) {
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
