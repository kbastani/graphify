package org.neo4j.nlp.examples.author;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

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

    final public static String[] reaganTraining = new String[]{
            "src/resources/reagan/training/1.txt",
            "src/resources/reagan/training/2.txt",
            "src/resources/reagan/training/3.txt",
            "src/resources/reagan/training/4.txt"
    };

    final public static String[] reaganTest = new String[]{
            "src/resources/reagan/test/1.txt",
            "src/resources/reagan/test/2.txt"
    };

    final public static String[] reaganLabels = new String[]{
            "ronald-reagan",
            "republican",
            "liberal"
    };

    final public static String[] bush41Training = new String[]{
            "src/resources/bush41/training/1.txt",
            "src/resources/bush41/training/2.txt",
            "src/resources/bush41/training/3.txt",
            "src/resources/bush41/training/4.txt"
    };

    final public static String[] bush41Test = new String[]{
            "src/resources/bush41/test/1.txt",
            "src/resources/bush41/test/2.txt"
    };

    final public static String[] bush41Labels = new String[]{
            "bush41",
            "republican",
            "conservative"
    };

    final public static String[] clintonTraining = new String[]{
            "src/resources/clinton/training/1.txt",
            "src/resources/clinton/training/2.txt",
            "src/resources/clinton/training/3.txt",
            "src/resources/clinton/training/4.txt"
    };

    final public static String[] clintonTest = new String[]{
            "src/resources/clinton/test/1.txt",
            "src/resources/clinton/test/2.txt"
    };

    final public static String[] clintonLabels = new String[]{
            "bill-clinton",
            "democrat",
            "liberal"
    };

    final public static String[] bush43Training = new String[]{
            "src/resources/bush43/training/1.txt",
            "src/resources/bush43/training/2.txt",
            "src/resources/bush43/training/3.txt",
            "src/resources/bush43/training/4.txt"
    };

    final public static String[] bush43Test = new String[]{
            "src/resources/bush43/test/1.txt",
            "src/resources/bush43/test/2.txt"
    };

    final public static String[] bush43Labels = new String[]{
            "bush43",
            "republican",
            "conservative"
    };

    final public static String[] obamaTraining = new String[]{
            "src/resources/obama/training/1.txt",
            "src/resources/obama/training/2.txt",
            "src/resources/obama/training/3.txt",
            "src/resources/obama/training/4.txt"
    };

    final public static String[] obamaTest = new String[]{
            "src/resources/obama/test/1.txt",
            "src/resources/obama/test/2.txt"
    };

    final public static String[] obamaLabels = new String[]{
            "barack-obama",
            "democrat",
            "liberal"
    };

    public static void main(String[] args) throws IOException {

        train();
        test();

    }

    private static void test() throws IOException {
        System.out.println("Bush41");
        for (String path : bush41Test) {
            testOnText(readLargerTextFile(path));
        }

        System.out.println("Barack Obama:");
        for (String path : obamaTest) {
            testOnText(readLargerTextFile(path));
        }

        System.out.println("Bill Clinton:");
        for (String path : clintonTest) {
            testOnText(readLargerTextFile(path));
        }

        System.out.println("Ronald Reagan:");
        for (String path : reaganTest) {
            testOnText(readLargerTextFile(path));
        }

        System.out.println("George H.W. Bush:");
        for (String path : bush43Test) {
            testOnText(readLargerTextFile(path));
        }
    }

    private static void train() throws IOException {
        for (String path : reaganTraining) {
            trainOnText(new String[]{readLargerTextFile(path)}, reaganLabels);
        }

        for (String path : bush41Training) {
            trainOnText(new String[]{readLargerTextFile(path)}, bush41Labels);
        }

        for (String path : clintonTraining) {
            trainOnText(new String[]{readLargerTextFile(path)}, clintonLabels);
        }

        for (String path : bush43Training) {
            trainOnText(new String[]{readLargerTextFile(path)}, bush43Labels);
        }

        for (String path : obamaTraining) {
            trainOnText(new String[]{readLargerTextFile(path)}, obamaLabels);
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

    private static void testOnText(String text) {

        JsonObject jsonParam = new JsonObject();
        jsonParam.add("text", new JsonPrimitive(text));

        String jsonPayload = new Gson().toJson(jsonParam);

        System.out.println(executePost("http://localhost:7474/service/graphify/classify", jsonPayload));
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

    public static String readLargerTextFile(String aFileName) throws IOException {

        StringBuilder sb = new StringBuilder();

        Path path = Paths.get(aFileName);
        try (Scanner scanner = new Scanner(path, ENCODING.name())) {
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine()).append("\n");
            }
        }
        
        String result = sb.toString();

        return result;
    }
}
