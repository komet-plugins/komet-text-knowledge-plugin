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
import network.ike.komet.text.recognize.Description;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * A fingerprint of the descriptions a matcher read: two runs with equal fingerprints matched
 * against the same descriptions, whatever their data versions say (ike-lab-documents topic
 * {@code dev-conceptification-match-provenance}).
 *
 * <p>It is a SHA-256 digest over one line per description, its text as matched, its kind, whether
 * its case is significant, and its concept's public ids, the lines sorted, so neither the order the
 * descriptions were read in nor the nids a store gave the concepts changes it.</p>
 */
public final class DescriptionFingerprint {

    /** The prefix that names the digest. */
    public static final String PREFIX = "sha256:";

    private DescriptionFingerprint() {
    }

    /**
     * The fingerprint of a set of descriptions.
     *
     * @param descriptions the descriptions the matcher read
     * @param publicIdOf   the public id of a concept, by the nid a description holds
     * @return {@code sha256:} and the digest in hexadecimal
     */
    public static String of(Collection<Description> descriptions, LongFunction<PublicId> publicIdOf) {
        List<String> lines = new ArrayList<>(descriptions.size());
        for (Description description : descriptions) {
            UUID[] uuids = publicIdOf.apply(description.conceptNid()).asUuidArray();
            String[] ids = new String[uuids.length];
            for (int i = 0; i < uuids.length; i++) {
                ids[i] = uuids[i].toString();
            }
            Arrays.sort(ids);
            lines.add(description.matchText() + '\t' + description.kind().name() + '\t'
                    + description.caseSignificant() + '\t' + String.join(",", ids));
        }
        lines.sort(null);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return PREFIX + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform provides SHA-256", e);
        }
    }
}
