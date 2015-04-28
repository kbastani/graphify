package org.neo4j.nlp.impl.util;

import edu.uci.ics.jung.graph.DirectedSparseGraph;

import java.util.*;

/**
 * Copyright (C) 2014 Kenny Bastani
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
public class PageRank {

    public static Map<Long, Double> calculatePageRank(Map<Long, LinkedHashMap<Long, Integer>> graphObject) {

        Map<Long, Double> result = new HashMap<>();
        DirectedSparseGraph<Long, Integer> graph = new DirectedSparseGraph<>();

        final Integer[] count = {0};

        graphObject.keySet().forEach(n -> graph.addVertex(n));

        graphObject.keySet().forEach(g ->
        {
            graphObject.get(g).keySet().forEach(lh ->
            {
                for (int i = 0; i < graphObject.get(g).get(lh); i++) {
                    graph.addEdge(count[0], g, lh);
                    count[0]++;
                }
            });
        });

        edu.uci.ics.jung.algorithms.scoring.PageRank<Long, Integer> ranker = new edu.uci.ics.jung.algorithms.scoring.PageRank<>(graph, 0.15);
        ranker.evaluate();
        double sum = 0;
        Set<Long> sortedVerticesSet =
                new TreeSet<Long>(graph.getVertices());
        for (Long v : sortedVerticesSet) {
            double score = ranker.getVertexScore(v);
            result.put(v, score);
            sum += score;
            //System.out.println(v + " = " + score);
        }
        //System.out.println("s = " + sum);
        return result;
    }
}
