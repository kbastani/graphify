package org.neo4j.nlp.impl.manager;

import org.junit.Assert;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

public class NodeManagerTest {

    @Test
    public void testGetOrCreateNode() throws Exception {
        GraphDatabaseService db = setUpDb();
        NodeManager nodeManager = new NodeManager();
        NodeManager.globalNodeCache.invalidateAll();
        DataNodeManager.dataCache.invalidateAll();
        DataNodeManager dataNodeManager = new DataNodeManager();

        // Write some nodes to the database
        Node a = nodeManager.getOrCreateNode(dataNodeManager, "a", db);
        Node b = nodeManager.getOrCreateNode(dataNodeManager, "b", db);
        Node c = nodeManager.getOrCreateNode(dataNodeManager, "c", db);
        Node d = nodeManager.getOrCreateNode(dataNodeManager, "d", db);
        Node e = nodeManager.getOrCreateNode(dataNodeManager, "e", db);
        Node f = nodeManager.getOrCreateNode(dataNodeManager, "f", db);
        Node g = nodeManager.getOrCreateNode(dataNodeManager, "g", db);

        Assert.assertNotNull(a);
        Assert.assertNotNull(b);
        Assert.assertNotNull(c);
        Assert.assertNotNull(d);
        Assert.assertNotNull(e);
        Assert.assertNotNull(f);
        Assert.assertNotNull(g);

        String expected = "a";

        Transaction tx = db.beginTx();
        String actual = (String)a.getProperty("value");
        tx.success();
        Assert.assertEquals(expected, actual);
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
        DataNodeManager dataNodeManager = new DataNodeManager();

        // Write some nodes to the database
        Transaction tx1 = db.beginTx();
        Node a = nodeManager.getOrCreateNode(dataNodeManager, "a", db);
        tx1.success();
        Assert.assertNotNull(a);

        String expected = "success";
        nodeManager.setNodeProperty(a.getId(), "test", expected, db);
        Transaction tx = db.beginTx();
        String actual = (String)NodeManager.getNodeFromGlobalCache(a.getId()).get("test");
        tx.success();

        Assert.assertEquals(expected, actual);
    }
}