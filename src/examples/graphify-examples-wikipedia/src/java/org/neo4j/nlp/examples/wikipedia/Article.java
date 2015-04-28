package org.neo4j.nlp.examples.wikipedia;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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
public class Article {
    public static String getExtract(String json) {
        Gson gson = new Gson();
        Map map;
        map = gson.fromJson(json, HashMap.class);
        String result = null;
        try {
            result = ((Map)((Map)((Map)map.get("query")).get("pages")).values().toArray()[0]).get("extract").toString();
        } catch(Exception ex) {
            Logger.getAnonymousLogger().info(ex.getMessage());
            Logger.getAnonymousLogger().info(json);
        }
        return result;
    }
}
