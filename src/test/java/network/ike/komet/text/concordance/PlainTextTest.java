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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inline markup removed from AsciiDoc text, with each character of the plain text mapped back to
 * the column of the source it came from.
 */
class PlainTextTest {

    @Test
    void indexTermsAreDroppedOrReducedToTheirVisibleWords() {
        PlainText plain = mapsBack("indexterm:[ANF, lisinopril]((lisinopril)) 20 mg oral tablet");
        assertEquals("lisinopril 20 mg oral tablet", plain.text());
        assertEquals(plain.source().indexOf("lisinopril))"), plain.sourceStart(0));
        assertEquals("lisinopril)) 20 mg oral tablet",
                plain.source().substring(plain.sourceStart(0), plain.sourceEnd(plain.text().length())));
    }

    @Test
    void aBibliographyAnchorIsRemovedWhole() {
        PlainText plain = mapsBack("[[[austin1962]]] Austin, J.L.");
        assertEquals("Austin, J.L.", plain.text());
        assertEquals(plain.source().indexOf("Austin"), plain.sourceStart(0));
    }

    @Test
    void textOfNothingButMarkupIsEmpty() {
        assertEquals("", mapsBack("indexterm:[ANF, medication history example]").text());
        assertEquals("", mapsBack("(((ANF, hidden))) [[an-anchor]]").text());
    }

    @Test
    void aKonceptTokenIsReadAsItsLabel() {
        PlainText plain = mapsBack("The k:3c2f[Blood pressure] is high.");
        assertEquals("The Blood pressure is high.", plain.text());
        int at = plain.text().indexOf("Blood pressure");
        assertEquals("Blood pressure", plain.source().substring(plain.sourceStart(at),
                plain.sourceEnd(at + "Blood pressure".length())));
    }

    @Test
    void linksAndCrossReferencesAreReadAsTheirText() {
        PlainText plain = mapsBack("See https://example.org/x[the label] and xref:topic[the topic] <<ref>> now.");
        assertEquals("See the label and the topic now.", plain.text());
        assertEquals("", mapsBack("https://example.org/bare").text());
    }

    @Test
    void formattingMarksAndHardBreaksAreRemoved() {
        PlainText plain = mapsBack("*Bold* and _italic_ text +");
        assertEquals("Bold and italic text", plain.text());
        assertEquals(1, plain.sourceStart(0), "the B after the asterisk");
    }

    @Test
    void aRunOfWhiteSpaceBecomesOneSpaceWhereTheRunBegan() {
        PlainText plain = mapsBack("  a \n\t b ");
        assertEquals("a b", plain.text());
        assertEquals(2, plain.sourceStart(0));
        assertEquals(3, plain.sourceStart(1));
        assertEquals(7, plain.sourceStart(2));
    }

    @Test
    void anOffsetOutsideThePlainTextIsRefused() {
        PlainText plain = PlainText.of("*a* b");
        assertThrows(IndexOutOfBoundsException.class, () -> plain.sourceStart(plain.text().length()));
        assertThrows(IndexOutOfBoundsException.class, () -> plain.sourceEnd(0));
        assertThrows(IndexOutOfBoundsException.class, () -> plain.sourceEnd(plain.text().length() + 1));
    }

    @Test
    void theFileReaderCleansTheSameWay() {
        String line = "indexterm:[liver]((Hepatic impairment)) and *k:x[Renal impairment]*";
        assertEquals(PlainText.of(line).text(), AsciiDocSentences.clean(line));
    }

    /**
     * Removes the markup and checks that each character of the plain text is the character of the
     * source it maps to, or a space where the source has white space.
     */
    private static PlainText mapsBack(String source) {
        PlainText plain = PlainText.of(source);
        int previous = -1;
        for (int i = 0; i < plain.text().length(); i++) {
            char read = plain.text().charAt(i);
            int column = plain.sourceStart(i);
            char written = source.charAt(column);
            assertTrue(read == written || (read == ' ' && Character.isWhitespace(written)),
                    "character " + i + " of '" + plain.text() + "' maps to column " + column + " of '" + source + "'");
            assertTrue(column > previous, "columns rise with the plain text");
            previous = column;
        }
        return plain;
    }
}
