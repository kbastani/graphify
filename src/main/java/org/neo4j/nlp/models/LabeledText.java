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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class represents a model for sending text to a REST API method
 * used to train natural language learning models.
 */
@XmlRootElement(name="labeledText")
@XmlAccessorType(XmlAccessType.FIELD)
public class LabeledText {
    @XmlElement(required=true)
    private String[] text;
    @XmlElement(required=true)
    private String[] label;
    @XmlElement(required=false)
    private int focus;

    public void setLabel(String[] label) {
        this.label = label;
    }

    public void setText(String[] text) {
        this.text = text;
    }

    public void setFocus(int focus) {
        this.focus = focus;
    }

    public String[] getLabel() {
        return label;
    }

    public String[] getText() {
        return text;
    }

    public int getFocus() {
        return focus;
    }

    public LabeledText() { }
}
