package org.neo4j.nlp.impl.util;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.nlp.helpers.GraphManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PatternMatcher extends RecursiveAction {
    private final String input;
    private final String regex;
    private final GraphDatabaseService db;
    private final GraphManager graphManager;
    private final Map<Long, Integer> matches;
    private final List<String> childPatterns;
    private final Long nodeId;

    private PatternMatcher(String regex, String input, Map<Long, Integer> matches, GraphDatabaseService db, GraphManager graphManager) {
        this.regex = regex;
        this.input = input;
        this.matches = matches;
        this.db = db;
        this.graphManager = graphManager;
        this.nodeId = graphManager.getOrCreateNode(regex, db).getId();
        this.childPatterns = graphManager.getNextLayer(nodeId, db);
    }

    @Override
    protected void compute() {
        int matchCount = getPatternMatchers();

        if (matchCount == 0) {
            return;
        }

        this.matches.put(nodeId, matchCount);

        List<PatternMatcher> tasks = childPatterns
                .stream()
                .map(pattern -> new PatternMatcher(pattern, input, matches, db, graphManager))
                .collect(Collectors.toList());

        invokeAll(tasks);
    }

    Integer getPatternMatchers() {
        // Match on input
        Pattern p = Pattern.compile("(?i)" + this.regex);
        Matcher m = p.matcher(this.input);
        Integer matchCount = 0;
        while(m.find())
            matchCount++;
        return matchCount;
    }

    public static Map<Long, Integer> match(String regex, String input, GraphDatabaseService db, GraphManager graphManager) {
        Map<Long, Integer> matches = new HashMap<>();
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new PatternMatcher(regex, input, matches, db, graphManager));

        // Cleaning up after yourself is important
        pool.shutdown();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return matches;
    }
}