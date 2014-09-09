package org.neo4j.nlp.examples.wikipedia;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

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
public class main {

    public static void main(String[] args) throws IOException {
        List<Map<String, Object>> results = getWikipediaArticles();

        //System.out.println(results);
        // Train model
        results.stream().filter(row -> (!row.get("text").toString().equals(""))).forEach(row -> {
            System.out.println("Training on '" + row.get("title").toString() + "'");
            trainOnText(new String[]{(String) row.get("text")}, new String[]{(String) row.get("title")});
        });

        //System.out.println(results);
    }

    private static List<Map<String, Object>> getWikipediaArticles() throws IOException {
        final String txUri = "http://localhost:7474/db/data/" + "transaction/commit";
        WebResource resource = Client.create().resource( txUri );

        String query = "MATCH (n:Page) WHERE n.text <> '' WITH n, rand() as sortOrder " +
                "ORDER BY sortOrder " +
                "LIMIT 300 " +
                "RETURN n.title as title, n.text as text;";

        String payload = "{\"statements\" : [ {\"statement\" : \"" +query + "\"} ]}";
        ClientResponse response = resource
                .accept( MediaType.APPLICATION_JSON )
                .type( MediaType.APPLICATION_JSON )
                .entity( payload )
                .post( ClientResponse.class );

        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, Object> result;
        try {
            result = objectMapper.readValue(response.getEntity( String.class ), HashMap.class);
        } catch (Exception e) {
            throw e;
        }
        response.close();

        List<Map<String, Object>> results = new ArrayList<>();

        ArrayList resultSet = ((ArrayList)result.get("results"));
        List<LinkedHashMap<String, Object>> dataSet = (List<LinkedHashMap<String, Object>>)resultSet.stream().map(a -> (LinkedHashMap<String, Object>)a).collect(Collectors.toList());

        List<LinkedHashMap> rows = (List<LinkedHashMap>)((ArrayList)(dataSet.get(0).get("data"))).stream().map(m -> (LinkedHashMap)m).collect(Collectors.toList());
        ArrayList cols = (ArrayList)(dataSet.get(0).get("columns"));

        for(LinkedHashMap row : rows) {
            ArrayList values = (ArrayList)row.get("row");
            Map<String, Object> resultRecord = new HashMap<>();
            for (int i = 0; i < values.size(); i++) {
                resultRecord.put(cols.get(i).toString(), values.get(i));
            }
            results.add(resultRecord);
        }
        return results;
    }

    private static void trainOnText(String[] text, String[] label) {
        List<String> labelSet = new ArrayList<>();
        List<String> textSet = new ArrayList<>();

        Collections.addAll(labelSet, label);
        Collections.addAll(textSet, text);

        JsonArray labelArray = new JsonArray();
        JsonArray textArray = new JsonArray();

        labelSet.forEach((s) -> labelArray.add(new JsonPrimitive(s)));
        textSet.forEach((s) -> textArray.add(new JsonPrimitive(s)));

        JsonObject jsonParam = new JsonObject();
        jsonParam.add("text", textArray);
        jsonParam.add("label", labelArray);
        jsonParam.add("focus", new JsonPrimitive(2));

        String jsonPayload = new Gson().toJson(jsonParam);

        executePost("http://localhost:7474/service/graphify/training", jsonPayload);
    }

    private static void testOnText(String text) {

        JsonObject jsonParam = new JsonObject();
        jsonParam.add("text", new JsonPrimitive(text));

        String jsonPayload = new Gson().toJson(jsonParam);

        executePost("http://localhost:7474/service/graphify/classify", jsonPayload);
    }

    private static String executePost(String targetURL, String payload) {
        try {

            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpPost postRequest = new HttpPost(
                    targetURL);

            StringEntity input = new StringEntity(payload);
            input.setContentType("application/json");
            postRequest.setEntity(input);

            HttpResponse response = httpClient.execute(postRequest);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + response.getStatusLine().getStatusCode());
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            StringBuilder output = new StringBuilder();
            while (br.read() != -1) {
                output.append(br.readLine()).append('\n');
            }

            httpClient.getConnectionManager().shutdown();

            return output.toString();

        } catch (IOException e) {

            e.printStackTrace();

        }

        return null;
    }
}
