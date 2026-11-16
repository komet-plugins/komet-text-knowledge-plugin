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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Matched words read in their stored sentence, and clipped to a few words on each side. */
class InContextTest {

    private static final String SENTENCE = "Each new clinical use case generates new structural variations.";

    @Test
    void theWordsAreReadBetweenTheirColumns() {
        int from = SENTENCE.indexOf("case");
        InContext context = InContext.of(SENTENCE, from, from + 4).orElseThrow();
        assertEquals(new InContext("Each new clinical use ", "case", " generates new structural variations."), context);
        assertEquals(SENTENCE, context.text());
    }

    @Test
    void markupInTheStoredTextIsReadAsPlainText() {
        String stored = "But each new _use case_ needs k:uuid=00000000-0000-4000-8000-000000000001[another epicycle].";
        int from = stored.indexOf("case");
        InContext context = InContext.of(stored, from, from + 4).orElseThrow();
        assertEquals(new InContext("But each new use ", "case", " needs another epicycle."), context);
        // columns that take in the markup around the words read the words alone
        int under = stored.indexOf("_use");
        assertEquals("use case", InContext.of(stored, under, stored.indexOf("case_") + 5).orElseThrow().words());
    }

    @Test
    void columnsOutsideTheSentenceOrOverMarkupAloneReadNothing() {
        assertEquals(Optional.empty(), InContext.of(SENTENCE, 10, 200), "past the end, as after a revision");
        assertEquals(Optional.empty(), InContext.of(SENTENCE, 5, 5));
        assertEquals(Optional.empty(), InContext.of(SENTENCE, -1, 3));
        assertEquals(Optional.empty(), InContext.of("A *bold* word.", 2, 3), "an asterisk alone is no text");
    }

    @Test
    void aClippedSideEndsInAnEllipsis() {
        int from = SENTENCE.indexOf("case");
        InContext context = InContext.of(SENTENCE, from, from + 4).orElseThrow();
        assertEquals(new InContext("… clinical use ", "case", " generates new …"), context.clipped(2));
        assertEquals(new InContext("… ", "case", " …"), context.clipped(0));
        assertEquals(context, context.clipped(4), "four before and four after: nothing is cut");
        assertEquals(context, context.clipped(8));
        assertEquals(new InContext("", "Each", " new …"), InContext.of(SENTENCE, 0, 4).orElseThrow().clipped(1));
        assertThrows(IllegalArgumentException.class, () -> context.clipped(-1));
    }
}
