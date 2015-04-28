package org.neo4j.nlp.examples.sentiment;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang.ArrayUtils;
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
import java.util.stream.IntStream;

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
    private static final int SAMPLE_SIZE = 1000;
    final private static Stack<Integer> NEGATIVE_RANDOMIZED_INDEX = getRandomizedIndex(0, SAMPLE_SIZE);
    final private static Stack<Integer> POSITIVE_RANDOMIZED_INDEX = getRandomizedIndex(0, SAMPLE_SIZE);
    final static Integer trainCount = 200;
    final static Integer testCount = 200;

    public static void main(String[] args) throws IOException {
        train();
        System.out.println(test(testCount));
    }

    private static Stack<Integer> getRandomizedIndex(int lowerBound, int upperBound)
    {
        List<Integer> randomIndex = new ArrayList<>();

        Collections.addAll(randomIndex, ArrayUtils.toObject(IntStream.range(lowerBound, upperBound).toArray()));

        //randomIndex.sort((a, b) -> new Random().nextInt(2) == 0 ? -1 : 1 );

        Stack<Integer> integerStack = new Stack<>();

        integerStack.addAll(randomIndex);

        return integerStack;
    }

    private static Map<String, Double> test(Integer testCount) throws IOException {
        Integer negativeError = 0;
        Integer positiveError = 0;
        Integer totalCount = 0;
        Integer negativeStep = 0;
        Integer positiveStep = 0;
        Integer allStep = 0;

        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 1; i < testCount * 2; i++) {
            if(i % 2 == 0)
            {
                int result = testOnText(negativeText.get(NEGATIVE_RANDOMIZED_INDEX.pop()), "negative") ? 0 : 1;
                totalCount += result;
                negativeError += result;
                negativeStep += 1;
                // Status update
                System.out.println("Negative: " + (1.0 - (negativeError.doubleValue() / negativeStep)));
            } else {
                int result = testOnText(positiveText.get(POSITIVE_RANDOMIZED_INDEX.pop()), "positive") ? 0 : 1;
                totalCount += result;
                positiveError += result;
                positiveStep += 1;
                // Status update
                System.out.println("Positive: " + (1.0 - (positiveError.doubleValue() / positiveStep)));
            }

            allStep += 1;

            // Status update
            System.out.println("All: " + (1.0 - ((negativeError.doubleValue() + positiveError.doubleValue()) / allStep)));
        }

        Map<String, Double> errorMap = new HashMap<>();

        errorMap.put("negative", 1.0 - (negativeError.doubleValue() / testCount.doubleValue()));
        errorMap.put("positive", 1.0 - (positiveError.doubleValue() / testCount.doubleValue()));
        errorMap.put("all", 1.0 - (totalCount.doubleValue() / (testCount.doubleValue() * 2)));

        // Return success ratio
        return errorMap;
    }

    private static void train() throws IOException {
        List<String> negativeText = readLargerTextFile(negativeSentimentDirectory);
        List<String> positiveText = readLargerTextFile(positiveSentimentDirectory);

        for (int i = 0; i < trainCount * 2; i++) {
            if(i % 2 == 0) {
                trainOnText(new String[] { negativeText.get(NEGATIVE_RANDOMIZED_INDEX.pop()) }, new String[]{"negative"});
            } else {
                trainOnText(new String[] { positiveText.get(POSITIVE_RANDOMIZED_INDEX.pop()) }, new String[]{"positive"});
            }
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
        jsonParam.add("focus", new JsonPrimitive(1));

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
