package org.neo4j.nlp.examples.sentiment;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Copyright (C) 2014 Kenny Bastani
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
class main {

    final public static Charset ENCODING = StandardCharsets.UTF_8;

    final public static String negativeSentimentDirectory = "src/main/resources/txt_sentoken/neg";
    final public static String positiveSentimentDirectory = "src/main/resources/txt_sentoken/pos";


    public static void main(String[] args) throws IOException {
        train();
        System.out.println(test());
    }

    final static Integer trainCount = 600;

    private static Map<String, Double> test() throws IOException {
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);

        Integer negativeError = 0;
        Integer positiveError = 0;

        for(String text : negativeText.stream().skip(trainCount).limit(trainCount).collect(Collectors.toList()))
        {
            negativeError += testOnText(text, "negative") ? 0 : 1;
        }

        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for(String text : positiveText.stream().skip(trainCount).limit(trainCount).collect(Collectors.toList()))
        {
            positiveError += testOnText(text, "positive") ? 0 : 1;
        }

        Map<String, Double> errorMap = new HashMap<String, Double>();

        errorMap.put("negative", 1.0 - (negativeError.doubleValue() / trainCount.doubleValue()));
        errorMap.put("positive", 1.0 - (positiveError.doubleValue() / trainCount.doubleValue()));
        // Return success ratio
        return errorMap;
    }

    private static void train() throws IOException {

        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);

        for(String text : negativeText.stream().skip(500).limit(100).collect(Collectors.toList()))
        {
            trainOnText(new String[] { text }, new String[]{"negative"});
        }

        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for(String text : positiveText.stream().skip(500).limit(100).collect(Collectors.toList()))
        {
            trainOnText(new String[] { text }, new String[]{"positive"});
        }


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

        System.out.println(executePost("http://localhost:7474/service/graphify/training", jsonPayload));
    }

    private static boolean testOnText(String text, String label) {

        JsonObject jsonParam = new JsonObject();
        jsonParam.add("text", new JsonPrimitive(text));

        String jsonPayload = new Gson().toJson(jsonParam);

        String input = executePost("http://localhost:7474/service/graphify/classify", jsonPayload);

        System.out.println(input);

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> hashMap = new HashMap<>();
        try {
            hashMap = (Map<String, Object>)objectMapper.readValue(input, HashMap.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Validate guess
        ArrayList classes = (ArrayList)hashMap.get("classes");

        if(classes.size() > 0) {
            LinkedHashMap className = (LinkedHashMap) classes.stream().findFirst().get();

            return className.get("class").equals(label);
        } else {
            return false;
        }
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

            return "{" + output.toString();

        } catch (IOException e) {

            e.printStackTrace();

        }

        return null;
    }

    public static List<String> readLargerTextFile(String aFileName) throws IOException {

        List<String> files = new ArrayList<>();
        File fileDir = new File(aFileName);

        for (final File fileEntry : fileDir.listFiles()) {
            StringBuilder sb = new StringBuilder();

            Path path = Paths.get(aFileName + "/" + fileEntry.getName());
            try (Scanner scanner = new Scanner(path, ENCODING.name())) {
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine()).append("\n");
                }
            }

            String result = sb.toString();
            files.add(result);
        }

        return files;
    }
}
