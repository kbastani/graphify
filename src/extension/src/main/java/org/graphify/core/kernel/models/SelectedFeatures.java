package org.graphify.core.kernel.models;

/**
 * Copyright (C) 2014 Kenny Bastani
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * The SelectedFeatures model is used to select a feature target for building machine learning models.
 */
public class SelectedFeatures {
    private List<String> labels = new ArrayList<String>() {
        {
            add("positive");
            add("negative");
        }
    };
    private String type = "binary";

    public SelectedFeatures()
    {

    }

    public SelectedFeatures(List<String> labels, String type) {
        this.labels = labels;
        this.type = type;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "[" + "labels=" + labels + ", type=" + type + "]";
    }
}
