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
package network.ike.komet.text.concordance;

/**
 * Why a file of a repository was not read from the store, so the report can say what it left out
 * and why.
 */
public enum NotRead {
    /** A file of {@code include::} directives; the files it includes are read on their own. */
    ASSEMBLY,
    /** A file the repository's HEAD does not hold, so the store would have nothing of it to read. */
    NOT_COMMITTED,
    /** A document the store holds no sentences for, such as one with no prose. */
    NO_STORED_SENTENCES
}
