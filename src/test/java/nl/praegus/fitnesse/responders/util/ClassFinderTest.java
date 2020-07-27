package nl.praegus.fitnesse.responders.util;

import nl.praegus.fitnesse.responders.AutoCompleteResponder;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFinderTest {
    private URLClassLoader classLoader;

    @Test
    public void classes_can_be_retrieved_from_classpath() throws Exception {
        String mainClassFile = this.getClass().getCanonicalName().replaceAll("\\.", "/") + ".class";
        URL[] url = new URL[]{this.getClass().getClassLoader().getResource(mainClassFile)};
        classLoader = new URLClassLoader(url, ClassLoader.getSystemClassLoader());
        List<Class> classList = ClassFinder.getClasses("nl.praegus.fitnesse.responders", false, classLoader);
        assertThat(classList.size()).isEqualTo(1);
        assertThat(classList).contains(AutoCompleteResponder.class);
    }

    @Test
    public void classes_are_retrieved_from_dirs_with_spaces() throws Exception {

        URL[] url = new URL[]{getClass().getClassLoader().getResource("dir with spaces/TestClass.jar").toURI().toURL()};
        classLoader = new URLClassLoader(url, ClassLoader.getSystemClassLoader());
        List<Class> classList = ClassFinder.getClasses("nl.praegus.testclass", false, classLoader);
        assertThat(classList.size()).isEqualTo(1);

    }
}

