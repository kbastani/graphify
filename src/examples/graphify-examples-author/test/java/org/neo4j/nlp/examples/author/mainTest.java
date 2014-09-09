//package org.neo4j.nlp.examples.author;
//
//import com.google.gson.Gson;
//import org.junit.Test;
//import org.neo4j.graphdb.GraphDatabaseService;
//import org.neo4j.graphdb.Node;
//import org.neo4j.graphdb.Transaction;
//import org.neo4j.nlp.helpers.GraphManager;
//import org.neo4j.nlp.impl.cache.ClassRelationshipCache;
//import org.neo4j.nlp.impl.cache.PatternRelationshipCache;
//import org.neo4j.nlp.impl.manager.ClassNodeManager;
//import org.neo4j.nlp.impl.manager.DataNodeManager;
//import org.neo4j.nlp.impl.manager.DataRelationshipManager;
//import org.neo4j.nlp.impl.manager.NodeManager;
//import org.neo4j.nlp.impl.util.LearningManager;
//import org.neo4j.nlp.impl.util.VectorUtil;
//import org.neo4j.test.TestGraphDatabaseFactory;
//
//import java.io.IOException;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.text.BreakIterator;
//import java.util.*;
//
//public class mainTest {
//
//    @Test
//    public void testAuthorRecognition() throws Exception {
//
//        NodeManager.globalNodeCache.invalidateAll();
//        DataNodeManager.dataCache.invalidateAll();
//        ClassNodeManager.classCache.invalidateAll();
//        GraphManager.edgeCache.invalidateAll();
//        GraphManager.inversePatternCache.invalidateAll();
//        GraphManager.patternCache.invalidateAll();
//        DataRelationshipManager.relationshipCache.invalidateAll();
//        ClassRelationshipCache.relationshipCache.invalidateAll();
//        PatternRelationshipCache.relationshipCache.invalidateAll();
//
//        GraphDatabaseService db = setUpDb();
//        GraphManager graphManager = new GraphManager("Pattern");
//
//        for (String path : main.reaganTraining) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.reaganLabels));
//        }
//
//        for (String path :  main.bush41Training) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.bush41Labels));
//        }
//
//        for (String path :  main.clintonTraining) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.clintonLabels));
//        }
//
//        for (String path :  main.bush43Training) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.bush43Labels));
//        }
//
//        for (String path :  main.obamaTraining) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.obamaLabels));
//        }
//
//        for (String path : main.reaganTraining) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.reaganLabels));
//        }
//
//        for (String path :  main.bush41Training) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.bush41Labels));
//        }
//
//        for (String path :  main.clintonTraining) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.clintonLabels));
//        }
//
//        for (String path :  main.bush43Training) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.bush43Labels));
//        }
//
//        for (String path :  main.obamaTraining) {
//            System.out.println(trainOnTextModel(db, graphManager, new String[]{main.readLargerTextFile(path)}, main.obamaLabels));
//        }
//
//        VectorUtil.vectorSpaceModelCache.invalidateAll();
//
//        System.out.println("Obama");
//        for (String path : main.obamaTest) {
//            System.out.println(testOnTextModel(main.readLargerTextFile(path), db, graphManager));
//        }
//
//        System.out.println("Bush");
//        for (String path : main.bush43Test) {
//            System.out.println(testOnTextModel(main.readLargerTextFile(path), db, graphManager));
//        }
//
//        System.out.println("Clinton");
//        for (String path : main.clintonTest) {
//            System.out.println(testOnTextModel(main.readLargerTextFile(path), db, graphManager));
//        }
//
//        System.out.println("George H.W. Bush");
//        for (String path : main.bush41Test) {
//            System.out.println(testOnTextModel(main.readLargerTextFile(path), db, graphManager));
//        }
//
//        System.out.println("Reagan");
//        for (String path : main.reaganTest) {
//            System.out.println(testOnTextModel(main.readLargerTextFile(path), db, graphManager));
//        }
//
//    }
//
//    private String testOnTextModel(String text, GraphDatabaseService db, GraphManager graphManager) {
//        //Clean text
//        text = text.replaceAll("(,|:|;)", " ");
//        text = text.replaceAll("  ", " ");
//        text = text.replaceAll("\\n", "");
//
//        return new Gson().toJson(VectorUtil.similarDocumentMapForVector(db, graphManager, text));
//    }
//
//    private static GraphDatabaseService setUpDb()
//    {
//        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
//    }
//
//    private static String trainOnTextModel(GraphDatabaseService db, GraphManager graphManager, String[] text, String[] label)
//    {
//        //Clean text
//        text[0] = text[0].replaceAll("(,|:|;)", " ");
//        text[0] = text[0].replaceAll("  ", " ");
//        text[0] = text[0].replaceAll("\\n", "");
//        text[0] = text[0].replaceAll("([\\.])", " block. block ");
//        text[0] = text[0].replaceAll("  ", " ");
//
//        Transaction tx = db.beginTx();
//        getRootPatternNode(db, graphManager);
//        LearningManager.trainInput(Arrays.asList(text), Arrays.asList(label), graphManager, db);
//        tx.success();
//        tx.close();
//
//        return "success";
//    }
//
//    private static Node getRootPatternNode(GraphDatabaseService db, GraphManager graphManager) {
//        Node patternNode;
//        patternNode = new NodeManager().getOrCreateNode(graphManager, GraphManager.ROOT_TEMPLATE, db);
//        if(!patternNode.hasProperty("matches")) {
//            patternNode.setProperty("matches", 0);
//            patternNode.setProperty("threshold", GraphManager.MIN_THRESHOLD);
//            patternNode.setProperty("root", 1);
//            patternNode.setProperty("phrase", "{0} {1}");
//        }
//        return patternNode;
//    }
//
//    public static List<String> readLargerTextFile(String aFileName) throws IOException {
//
//        StringBuilder sb = new StringBuilder();
//
//        Path path = Paths.get(aFileName);
//        try (Scanner scanner = new Scanner(path, main.ENCODING.name())) {
//            while (scanner.hasNextLine()) {
//                sb.append(scanner.nextLine()).append("\n");
//            }
//        }
//
//        BreakIterator iterator =
//                BreakIterator.getSentenceInstance(Locale.US);
//
//        return getSentences(iterator, sb.toString());
//    }
//
//    private static List<String> getSentences(BreakIterator bi, String source) {
//        bi.setText(source);
//        List<String> sentences = new ArrayList<>();
//
//        int lastIndex = bi.first();
//        while (lastIndex != BreakIterator.DONE) {
//            int firstIndex = lastIndex;
//            lastIndex = bi.next();
//
//            if (lastIndex != BreakIterator.DONE) {
//                String sentence = source.substring(firstIndex, lastIndex);
//                sentences.add(sentence.trim().replaceAll("[,\"]", ""));
//                //System.out.println("sentence = " + sentence);
//            }
//        }
//        return sentences;
//    }
//
//}