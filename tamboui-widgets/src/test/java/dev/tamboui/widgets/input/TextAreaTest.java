/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.widgets.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;

import static org.assertj.core.api.Assertions.assertThat;

class TextAreaTest {

    @Test
    @DisplayName("render with WRAP_WORD wraps a long line across multiple screen rows")
    void renderWrapsAtWordBoundaries() {
        TextArea textArea = TextArea.builder().wrap(Overflow.WRAP_WORD).build();
        TextAreaState state = new TextAreaState("one two three");
        Buffer buffer = Buffer.empty(new Rect(0, 0, 7, 3));

        textArea.render(buffer.area(), buffer, state);

        assertThat(extractLineText(buffer, 0)).isEqualTo("one two");
        assertThat(extractLineText(buffer, 1)).isEqualTo("three");
        assertThat(extractLineText(buffer, 2)).isEmpty();
    }

    @Test
    @DisplayName("render with WRAP_CHARACTER splits a word longer than the viewport")
    void renderWrapsAtCharacterBoundaries() {
        TextArea textArea = TextArea.builder().wrap(Overflow.WRAP_CHARACTER).build();
        TextAreaState state = new TextAreaState("HelloWorld");
        Buffer buffer = Buffer.empty(new Rect(0, 0, 4, 3));

        textArea.render(buffer.area(), buffer, state);

        assertThat(extractLineText(buffer, 0)).isEqualTo("Hell");
        assertThat(extractLineText(buffer, 1)).isEqualTo("oWor");
        assertThat(extractLineText(buffer, 2)).isEqualTo("ld");
    }

    @Test
    @DisplayName("render with wrap and line numbers shows the number only on the first wrapped row")
    void renderWrapShowsLineNumberOnlyOnFirstRow() {
        TextArea textArea = TextArea.builder()
            .wrap(Overflow.WRAP_WORD)
            .showLineNumbers(true)
            .build();
        TextAreaState state = new TextAreaState("one two three");
        Buffer buffer = Buffer.empty(new Rect(0, 0, 11, 2));

        textArea.render(buffer.area(), buffer, state);

        // gutter is " 1 |" (right-padded digits + space + separator) on row 0, blank on row 1
        assertThat(buffer.get(1, 0).symbol()).isEqualTo("1");
        assertThat(buffer.get(3, 0).symbol()).isEqualTo("|");
        assertThat(buffer.get(1, 1).symbol()).isEqualTo(" ");
        assertThat(buffer.get(3, 1).symbol()).isEqualTo(" ");
    }

    @Test
    @DisplayName("renderWithCursor places the cursor on the correct wrapped row and column")
    void renderWithCursorOnWrappedRow() {
        Style cursorStyle = Style.EMPTY.reversed();
        TextArea textArea = TextArea.builder().wrap(Overflow.WRAP_WORD).cursorStyle(cursorStyle).build();
        TextAreaState state = new TextAreaState("one two three");
        state.moveCursorToEnd(); // end of "three", offset 13 -> wrapped row 1, col 5 ("three".length())

        Buffer buffer = Buffer.empty(new Rect(0, 0, 7, 3));
        Frame frame = Frame.forTesting(buffer);

        textArea.renderWithCursor(buffer.area(), buffer, state, frame);

        // Cursor row 1 ("three"), column 5 (just past 't','h','r','e','e')
        assertThat(buffer.get(5, 1).style()).isEqualTo(cursorStyle);
        assertThat(buffer.get(0, 0).style()).isNotEqualTo(cursorStyle);
    }

    private String extractLineText(Buffer buffer, int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < buffer.area().width(); x++) {
            if (!buffer.get(x, y).isContinuation()) {
                String sym = buffer.get(x, y).symbol();
                if (!sym.equals(" ") || sb.length() > 0) {
                    sb.append(sym);
                }
            }
        }
        return sb.toString().trim();
    }
}
