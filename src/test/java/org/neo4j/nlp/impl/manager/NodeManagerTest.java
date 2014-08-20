package org.neo4j.nlp.impl.manager;

import junit.framework.TestCase;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

public class NodeManagerTest extends TestCase {

    @Test
    public void testGetOrCreateNode() throws Exception {
        GraphDatabaseService db = setUpDb();
        NodeManager nodeManager = new NodeManager();
        NodeManager.globalNodeCache.invalidateAll();
        DataNodeManager.dataCache.invalidateAll();
        DataNodeManager dataNodeManager = new DataNodeManager("Data", "value");

        // Write some nodes to the database
        Node a = nodeManager.getOrCreateNode(dataNodeManager, "a", db);
        Node b = nodeManager.getOrCreateNode(dataNodeManager, "b", db);
        Node c = nodeManager.getOrCreateNode(dataNodeManager, "c", db);
        Node d = nodeManager.getOrCreateNode(dataNodeManager, "d", db);
        Node e = nodeManager.getOrCreateNode(dataNodeManager, "e", db);
        Node f = nodeManager.getOrCreateNode(dataNodeManager, "f", db);
        Node g = nodeManager.getOrCreateNode(dataNodeManager, "g", db);

        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);
        assertNotNull(e);
        assertNotNull(f);
        assertNotNull(g);

        String expected = "a";

        Transaction tx = db.beginTx();
        String actual = (String)a.getProperty("value");
        tx.success();
        assertEquals(expected, actual);
    }

    private GraphDatabaseService setUpDb() {
        return new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();
    }

    @Test
    public void testSetNodeProperty() throws Exception {
        GraphDatabaseService db = setUpDb();
        NodeManager nodeManager = new NodeManager();
        NodeManager.globalNodeCache.invalidateAll();
        DataNodeManager.dataCache.invalidateAll();
        DataNodeManager dataNodeManager = new DataNodeManager("Data", "value");

        // Write some nodes to the database
        Transaction tx1 = db.beginTx();
        Node a = nodeManager.getOrCreateNode(dataNodeManager, "a", db);
        tx1.success();
        assertNotNull(a);

        String expected = "success";
        nodeManager.setNodeProperty(a.getId(), "test", expected, db);
        Transaction tx = db.beginTx();
        String actual = (String)NodeManager.getNodeFromGlobalCache(a.getId()).get("test");
        tx.success();

        assertEquals(expected, actual);
    }
}