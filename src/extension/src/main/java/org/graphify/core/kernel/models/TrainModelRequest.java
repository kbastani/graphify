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
public class TrainModelRequest {
    private Double trainingRatio;
    private Integer targetId;

    public TrainModelRequest() {

    }

    public TrainModelRequest(Double trainingRatio, Integer targetId) {
        this.trainingRatio = trainingRatio;
        this.targetId = targetId;
    }

    public Double getTrainingRatio() {
        return trainingRatio;
    }

    public void setTrainingRatio(Double trainingRatio) {
        this.trainingRatio = trainingRatio;
    }

    public Integer getTargetId() {
        return targetId;
    }

    public void setTargetId(Integer targetId) {
        this.targetId = targetId;
    }
}
