package org.neo4j.nlp.impl.util;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.nlp.helpers.GraphManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternMatcher extends RecursiveAction {
    private String input;
    private String regex;
    private GraphDatabaseService db;
    private GraphManager graphManager;
    private List<Long> matches;
    private List<String> childPatterns;
    private Long nodeId;

    private PatternMatcher(String regex, String input, List<Long> matches, GraphDatabaseService db, GraphManager graphManager) {
        this.regex = regex;
        this.input = input;
        this.matches = matches;
        this.db = db;
        this.graphManager = graphManager;
        this.nodeId = graphManager.getOrCreateNode(regex, db).getId();
        this.childPatterns = graphManager.getNextLayer(nodeId, db);
        //compute();
    }

    @Override
    protected void compute() {
        if (!getPatternMatchers()) {
            return;
        }

        this.matches.add(nodeId);
        List<PatternMatcher> tasks = new ArrayList<>();

        for(String pattern : childPatterns) {
            tasks.add(new PatternMatcher(pattern, input, matches, db, graphManager));
        }

        invokeAll(tasks);
    }

    boolean getPatternMatchers() {
        // Match on input
        Pattern p = Pattern.compile("(?i)" + this.regex);
        Matcher m = p.matcher(this.input);
        return m.find();
    }

    public static List<Long> match(String regex, String input, GraphDatabaseService db, GraphManager graphManager) {
        List<Long> matches = new ArrayList<>();
        ForkJoinPool pool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        pool.invoke(new PatternMatcher(regex, input, matches, db, graphManager));
        long endTime = System.currentTimeMillis();
        System.out.println("Image blur took " + (endTime - startTime) +
                " milliseconds.");
        return matches;
    }
}