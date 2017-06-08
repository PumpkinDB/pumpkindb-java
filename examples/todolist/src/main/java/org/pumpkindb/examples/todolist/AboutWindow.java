package org.pumpkindb.examples.todolist;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;

import java.util.Arrays;
import java.util.Scanner;

public class AboutWindow extends BasicWindow {

    private static String ABOUT = new Scanner(Main.class.getResourceAsStream("about.txt"), "UTF-8")
            .useDelimiter("\\A").next();

    private static String DIAGRAM = new Scanner(Main.class.getResourceAsStream("diagram.txt"), "UTF-8")
            .useDelimiter("\\A").next();

    public AboutWindow(TerminalSize terminalSize) {
        super("About");
        Panel p = new Panel(new BorderLayout());
        p.addComponent(new Label(ABOUT +
                                         (terminalSize.getColumns() >= 96 ? DIAGRAM :
                                                 "[Full diagram included but your terminal should\n" +
                                                         " be at least 96 characters wide to show it]")),
                       BorderLayout.Location.TOP);
        p.addComponent(new Button("Close", this::close), BorderLayout.Location.BOTTOM);
        setComponent(p);
        setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.EXPANDED));
    }
}
