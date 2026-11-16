/*
 * Copyright © 2026 Knowledge Graphlet / IKE Network
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.komet.text.recognize;

import java.util.Objects;

/**
 * A concept a stretch of words may name, with the stage that proposed it.
 *
 * @param conceptNid the nid of the concept
 * @param stage      the stage that proposed it
 */
public record Candidate(long conceptNid, Stage stage) {

    /**
     * Validates the candidate.
     *
     * @throws NullPointerException if {@code stage} is null
     */
    public Candidate {
        Objects.requireNonNull(stage, "stage");
    }
}
