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
package network.ike.komet.text.ui;

/**
 * Selects Monocle's headless JavaFX platform for a test that needs the FX thread and a scene graph
 * but asserts nothing the native toolkit renders, as the stamped document plugin's tests do. Call
 * it before {@code Platform.startup}. The native macOS toolkit started without a login session, as
 * on a build agent, prints an error on stderr that the build report counts as a warning, and
 * Monocle puts nothing on the screen of a machine that has one.
 */
final class HeadlessFx {

    /** The headless screen: width, height and bits a pixel. */
    private static final String SCREEN = "1600x1600-32";

    private HeadlessFx() {
    }

    /** Selects Monocle headless when it is on the class path; otherwise leaves the default toolkit. */
    static void select() {
        try {
            Class.forName("com.sun.glass.ui.monocle.MonoclePlatformFactory");
        } catch (ClassNotFoundException noMonocle) {
            return;
        }
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("headless.geometry", SCREEN);
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("java.awt.headless", "true");
    }
}
