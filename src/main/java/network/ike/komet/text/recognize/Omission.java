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

/**
 * Why a description is left out of the description maps: its words would match ordinary text
 * wherever that text uses them, whatever it means there.
 */
public enum Omission {

    /**
     * Made only of function words, such as "and", "in" or "is a", and not a name whose case is
     * significant and written with a capital, as the operator "AND" is.
     */
    FUNCTION_WORDS,

    /**
     * Made only of numbers, such as "1", "+1" or "10%". The tokenizer keeps the digits and drops a
     * sign or a percent mark, so such a description would match every such number in a text.
     * Numbers belong to the typed spans, which read a value with its unit.
     */
    NUMBERS
}
