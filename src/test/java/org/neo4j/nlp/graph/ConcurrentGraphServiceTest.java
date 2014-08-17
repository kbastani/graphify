package org.neo4j.nlp.graph;

import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;

public class ConcurrentGraphServiceTest {

    @Test
    public void sendReceiveMessageTest()
    {
        ConcurrentGraphService concurrentGraphService = new ConcurrentGraphService();
        String expected = "Hello world!";
        String actual = null;
        try {
            concurrentGraphService.sendMessage(expected);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            actual = concurrentGraphService.receiveMessage();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Assert.assertEquals(expected, actual);
    }
}