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
package network.ike.komet.text.matches;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import network.ike.komet.text.recognize.Description;
import network.ike.komet.text.recognize.DescriptionKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fingerprint of the descriptions a matcher read: the same for the same descriptions, whatever
 * their order or nids, and different when any description differs.
 */
class DescriptionFingerprintTest {

    private static final PublicId LIVER = PublicIds.of("6111dc03-0000-4000-8000-000000000001");
    private static final PublicId KIDNEY = PublicIds.of("6111dc03-0000-4000-8000-000000000002");

    @Test
    void theOrderAndTheNidsDoNotChangeIt() {
        Description a = new Description(1, "Hepatic impairment", DescriptionKind.REGULAR_NAME, false);
        Description b = new Description(2, "Renal impairment", DescriptionKind.REGULAR_NAME, false);
        String one = DescriptionFingerprint.of(List.of(a, b), ids(Map.of(1L, LIVER, 2L, KIDNEY)));
        Description c = new Description(20, "Renal impairment", DescriptionKind.REGULAR_NAME, false);
        Description d = new Description(10, "Hepatic impairment", DescriptionKind.REGULAR_NAME, false);
        assertEquals(one, DescriptionFingerprint.of(List.of(c, d), ids(Map.of(10L, LIVER, 20L, KIDNEY))));
        assertTrue(one.startsWith(DescriptionFingerprint.PREFIX));
        assertEquals(DescriptionFingerprint.PREFIX.length() + 64, one.length());
    }

    @Test
    void anyDifferenceInADescriptionChangesIt() {
        LongFunction<PublicId> ids = ids(Map.of(1L, LIVER, 2L, KIDNEY));
        String base = DescriptionFingerprint.of(List.of(new Description(1, "Hepatic impairment", DescriptionKind.REGULAR_NAME, false)), ids);
        assertNotEquals(base, DescriptionFingerprint.of(List.of(new Description(1, "Hepatic impairments", DescriptionKind.REGULAR_NAME, false)), ids));
        assertNotEquals(base, DescriptionFingerprint.of(List.of(new Description(1, "Hepatic impairment", DescriptionKind.FULLY_QUALIFIED_NAME, false)), ids));
        assertNotEquals(base, DescriptionFingerprint.of(List.of(new Description(1, "Hepatic impairment", DescriptionKind.REGULAR_NAME, true)), ids));
        assertNotEquals(base, DescriptionFingerprint.of(List.of(new Description(2, "Hepatic impairment", DescriptionKind.REGULAR_NAME, false)), ids));
    }

    private static LongFunction<PublicId> ids(Map<Long, PublicId> byNid) {
        return byNid::get;
    }
}
