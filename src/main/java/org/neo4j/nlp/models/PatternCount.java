/*
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

package org.neo4j.nlp.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class model represents a word frequency measurement to generate new genetic patterns
 * for a pattern recognition hierarchy.
 */
public class PatternCount
{
    private int count = 0;
    private String pattern = "";
    private final List<Map<String, Object>> dataNodes = new ArrayList<>();

    public PatternCount(String pattern, int count, Map<String, Object> dataNode)
    {
        this.count = count;
        this.pattern = pattern;
        if(dataNode != null)
            this.dataNodes.add(dataNode);
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void addDataNode(Map<String, Object> dataNode)
    {
        dataNodes.add(dataNode);
    }

    public String getPattern() {
        return pattern;
    }

    public int getCount() {
        return count;
    }

    public List<Map<String, Object>> getDataNodes()
    {
        return dataNodes;
    }


}
