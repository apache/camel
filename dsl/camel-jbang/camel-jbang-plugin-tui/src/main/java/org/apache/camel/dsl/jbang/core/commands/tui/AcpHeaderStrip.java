/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.image.Image;
import dev.tamboui.image.ImageData;
import dev.tamboui.image.ImageScaling;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import dev.tamboui.image.capability.TerminalImageProtocol;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.terminal.FrameInternal;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widget.RawOutputCapable;
import dev.tamboui.widget.Widget;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * Two-row header shown under the AI panel title while an ACP session is open: the agent's logo (native terminal
 * graphics only) or a coloured glyph, the preset and agent labels, and a dimmed line with session, working directory
 * and command count. ACP agents run headless and never draw a start screen of their own.
 */
final class AcpHeaderStrip {

    static final int ROWS = 2;
    private static final int LOGO_COLUMNS = 5;
    private static final int LOGO_PIXELS = 64;
    private static final long RESEND_INTERVAL_MS = 2_000;
    private static final String APC = "\033_G";
    private static final String ST = "\033\\";
    private static final int KITTY_CHUNK = 4096;
    private static final int KITTY_ID_BASE = 0x43_4D_00;

    enum LogoMode {
        AUTO,
        ON,
        OFF;

        static LogoMode parse(String value) {
            if (value == null) {
                return AUTO;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return AUTO;
            }
        }
    }

    record Model(String presetLabel, String glyph, Color color, String logo, String agentLabel, String sessionId,
            Path cwd, int commandCount) {
    }

    private final TerminalImageCapabilities capabilities;
    private final Map<String, ImageData> logos = new HashMap<>();
    private final Map<String, byte[]> logoBytes = new HashMap<>();
    private final Set<Integer> uploaded = new HashSet<>();
    private Rect lastLogoRect;
    private long lastLogoSentAt;
    private int placedImageId;

    AcpHeaderStrip() {
        this(TerminalImageCapabilities.detect());
    }

    AcpHeaderStrip(TerminalImageCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    boolean logosEnabled(LogoMode mode) {
        return switch (mode) {
            case ON -> true;
            case OFF -> false;
            default -> capabilities.supportsNativeImages();
        };
    }

    /** The preset's logo scaled to a small square, cached; null when there is no such resource or it cannot be read. */
    ImageData logoFor(String logo) {
        if (logo == null) {
            return null;
        }
        // computeIfAbsent does not store a null, so a missing logo would be looked up again on every frame
        if (logos.containsKey(logo)) {
            return logos.get(logo);
        }
        ImageData image = null;
        try (InputStream in = AcpHeaderStrip.class.getResourceAsStream("/tui/logos/" + logo + ".png")) {
            if (in != null) {
                image = ImageData.fromBytes(in.readAllBytes()).resize(LOGO_PIXELS, LOGO_PIXELS);
            }
        } catch (IOException | RuntimeException e) {
            image = null;
        }
        logos.put(logo, image);
        return image;
    }

    /** Raw PNG bytes of the preset logo (cached; null when missing or unreadable). Kitty scales them itself. */
    byte[] logoBytes(String logo) {
        if (logo == null) {
            return null;
        }
        if (logoBytes.containsKey(logo)) {
            return logoBytes.get(logo);
        }
        byte[] png = null;
        try (InputStream in = AcpHeaderStrip.class.getResourceAsStream("/tui/logos/" + logo + ".png")) {
            if (in != null) {
                png = in.readAllBytes();
            }
        } catch (IOException e) {
            png = null;
        }
        logoBytes.put(logo, png);
        return png;
    }

    void render(Frame frame, Rect area, Model model, LogoMode mode) {
        render(frame, area, model, mode, null);
    }

    void renderForTesting(Rect area, Buffer buffer, OutputStream raw, Model model, LogoMode mode) {
        render(Frame.forTesting(buffer), area, model, mode, raw);
    }

    private void render(Frame frame, Rect area, Model model, LogoMode mode, OutputStream rawOverride) {
        if (area.height() < ROWS || area.width() < 20) {
            return;
        }
        boolean enabled = logosEnabled(mode);
        boolean kitty = capabilities.supports(TerminalImageProtocol.KITTY);
        byte[] png = enabled && kitty ? logoBytes(model.logo()) : null;
        ImageData logo = enabled && !kitty ? logoFor(model.logo()) : null;
        boolean hasLogo = png != null || logo != null;
        Rect textArea = area;
        if (hasLogo) {
            List<Rect> parts = Layout.horizontal()
                    .constraints(Constraint.length(LOGO_COLUMNS), Constraint.length(1), Constraint.fill())
                    .split(area);
            if (png != null) {
                renderKittyLogo(frame, parts.get(0), kittyImageId(model.logo()), png, rawOverride);
            } else {
                renderLogo(frame, parts.get(0), logo);
            }
            textArea = parts.get(2);
        } else {
            lastLogoRect = null;
        }
        Style accent = Style.EMPTY.fg(model.color()).bold();
        String title = model.presetLabel() + " · " + model.agentLabel();
        Line first = hasLogo
                ? Line.from(Span.styled(title, accent))
                : Line.from(Span.styled(model.glyph() + " ", accent), Span.styled(title, accent));
        Line second = Line.from(Span.styled((hasLogo ? "" : "  ") + metaLine(model), Style.EMPTY.dim()));
        frame.renderWidget(Paragraph.from(first), new Rect(textArea.x(), textArea.y(), textArea.width(), 1));
        frame.renderWidget(Paragraph.from(second), new Rect(textArea.x(), textArea.y() + 1, textArea.width(), 1));
    }

    /**
     * Native protocols re-transmit the picture on every render, so the logo is sent only when its cell rectangle
     * changed or every RESEND_INTERVAL_MS, which keeps it visible after the terminal repaints without flooding it.
     */
    private void renderLogo(Frame frame, Rect logoArea, ImageData logo) {
        long now = System.currentTimeMillis();
        if (logoArea.equals(lastLogoRect) && now - lastLogoSentAt < RESEND_INTERVAL_MS) {
            return;
        }
        Image image = Image.builder()
                .data(logo)
                .scaling(ImageScaling.FIT)
                .protocol(capabilities.bestProtocol())
                .build();
        frame.renderWidget(image, logoArea);
        lastLogoRect = logoArea;
        lastLogoSentAt = now;
    }

    static String metaLine(Model model) {
        String session = model.sessionId() == null ? "?" : model.sessionId();
        if (session.length() > 8) {
            session = session.substring(0, 8);
        }
        String commands = model.commandCount() == 0
                ? "no commands yet"
                : model.commandCount() + (model.commandCount() == 1 ? " command" : " commands");
        return "session " + session + " · " + homeRelative(model.cwd()) + " · " + commands + " · /agent: lists them";
    }

    static String homeRelative(Path path) {
        if (path == null) {
            return "?";
        }
        String home = System.getProperty("user.home");
        String value = path.toAbsolutePath().toString();
        if (home != null && !home.isBlank() && value.startsWith(home)) {
            return "~" + value.substring(home.length());
        }
        return value;
    }

    /**
     * Kitty keeps the uploaded picture, so a frame only costs a placement command: no re-upload, no flash, and q=2
     * stops the terminal from answering on the input stream the TUI reads keys from.
     */
    private void renderKittyLogo(Frame frame, Rect logoArea, int imageId, byte[] png, OutputStream rawOverride) {
        KittyLogoWidget widget = new KittyLogoWidget(imageId, png);
        if (rawOverride != null) {
            widget.render(logoArea, frame.buffer(), rawOverride);
        } else {
            frame.renderWidget(widget, logoArea);
        }
        lastLogoRect = logoArea;
    }

    /**
     * Drops the kitty placement when the header stops being drawn; a no-op for every other terminal. Written straight
     * to the raw stream rather than through a widget: a widget would register its area with the frame, and TamboUI
     * would then blank that area with a space on the next frame that renders no raw output.
     */
    void hide(Frame frame) {
        hide(FrameInternal.rawOutput(frame));
    }

    void hideForTesting(OutputStream raw) {
        hide(raw);
    }

    private void hide(OutputStream rawOutput) {
        if (rawOutput == null || placedImageId == 0) {
            return;
        }
        try {
            rawOutput.write(kittyDelete(placedImageId).getBytes(StandardCharsets.US_ASCII));
            rawOutput.flush();
        } catch (IOException e) {
            // the terminal is gone, there is nothing left to clean up
        }
        placedImageId = 0;
        lastLogoRect = null;
    }

    /**
     * A stable, positive kitty image id per logo name, offset from a "CM" base so it does not clash with the ids of
     * other programs sharing the terminal. The range is 16 bits wide: 8 would put claude and codex on the same id.
     */
    static int kittyImageId(String logo) {
        return KITTY_ID_BASE + Math.floorMod(logo.hashCode(), 0xFFFF) + 1;
    }

    /** Transmits the PNG under an image id without displaying it, in chunks of at most KITTY_CHUNK base64 bytes. */
    static String kittyTransmit(int imageId, byte[] png) {
        String data = Base64.getEncoder().encodeToString(png);
        StringBuilder sb = new StringBuilder();
        for (int offset = 0, n = 0; offset < data.length() || n == 0; n++) {
            int end = Math.min(offset + KITTY_CHUNK, data.length());
            boolean more = end < data.length();
            sb.append(APC);
            if (n == 0) {
                sb.append("a=t,f=100,t=d,i=").append(imageId).append(",q=2,m=").append(more ? 1 : 0).append(';');
            } else {
                sb.append("m=").append(more ? 1 : 0).append(';');
            }
            sb.append(data, offset, end).append(ST);
            offset = end;
            if (!more) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Places the already transmitted image in the given cell box; placement id 1 replaces the previous placement and
     * C=1 keeps kitty from moving the cursor afterwards.
     */
    static String kittyPlace(int imageId, Rect area) {
        return "\033[" + (area.y() + 1) + ";" + (area.x() + 1) + "H"
               + APC + "a=p,i=" + imageId + ",p=1,c=" + area.width() + ",r=" + area.height() + ",C=1,q=2" + ST;
    }

    /** Deletes the placements of an image; the transmitted data stays, so the next frame only places it again. */
    static String kittyDelete(int imageId) {
        return APC + "a=d,d=i,i=" + imageId + ",q=2" + ST;
    }

    boolean isLogoCachedForTesting(String logo) {
        return logoBytes.containsKey(logo);
    }

    Rect lastLogoRectForTesting() {
        return lastLogoRect;
    }

    boolean hasPlacementForTesting() {
        return placedImageId != 0;
    }

    /**
     * Uploads the logo once per image id and then only places it. A widget because that is how TamboUI hands out the
     * terminal's raw stream; it draws nothing into the buffer.
     */
    private final class KittyLogoWidget implements Widget, RawOutputCapable {

        private final int imageId;
        private final byte[] png;

        KittyLogoWidget(int imageId, byte[] png) {
            this.imageId = imageId;
            this.png = png;
        }

        @Override
        public void render(Rect area, Buffer buffer) {
            render(area, buffer, null);
        }

        @Override
        public void render(Rect area, Buffer buffer, OutputStream rawOutput) {
            if (rawOutput == null) {
                return;
            }
            boolean fresh = uploaded.add(imageId);
            try {
                if (fresh) {
                    rawOutput.write(kittyTransmit(imageId, png).getBytes(StandardCharsets.US_ASCII));
                }
                rawOutput.write(kittyPlace(imageId, area).getBytes(StandardCharsets.US_ASCII));
                rawOutput.flush();
                placedImageId = imageId;
            } catch (IOException e) {
                uploaded.remove(imageId);
            }
        }
    }

}
