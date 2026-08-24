/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.widgets.input;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;

import static org.assertj.core.api.Assertions.assertThat;

class TextAreaTest {

    @Test
    @DisplayName("render with WRAP_WORD wraps a long line across multiple screen rows")
    void renderWrapsAtWordBoundaries() {
        TextArea textArea = TextArea.builder().overflow(Overflow.WRAP_WORD).build();
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
        TextArea textArea = TextArea.builder().overflow(Overflow.WRAP_CHARACTER).build();
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
            .overflow(Overflow.WRAP_WORD)
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
        TextArea textArea = TextArea.builder().overflow(Overflow.WRAP_WORD).cursorStyle(cursorStyle).build();
        TextAreaState state = new TextAreaState("one two three");
        state.moveCursorToEnd(); // end of "three", offset 13 -> wrapped row 1, col 5 ("three".length())

        Buffer buffer = Buffer.empty(new Rect(0, 0, 7, 3));
        Frame frame = Frame.forTesting(buffer);

        textArea.renderWithCursor(buffer.area(), buffer, state, frame);

        // Cursor row 1 ("three"), column 5 (just past 't','h','r','e','e')
        assertThat(buffer.get(5, 1).style()).isEqualTo(cursorStyle);
        assertThat(buffer.get(0, 0).style()).isNotEqualTo(cursorStyle);
    }

    @Test
    @DisplayName("renderWithCursor draws a cursor on word-wrap break whitespace at the start of the next row")
    void renderWithCursorOnConsumedWordWrapBreak() {
        Style cursorStyle = Style.EMPTY.reversed();
        TextArea textArea = TextArea.builder().overflow(Overflow.WRAP_WORD).cursorStyle(cursorStyle).build();
        TextAreaState state = new TextAreaState("one two three");
        state.moveCursorToEnd();
        // Up clamps into the first visual row "one two" [0,7): offset 7 is the space the wrap
        // consumed, so it belongs to no display row and must be drawn at the start of "three".
        state.moveCursorUp(7, Overflow.WRAP_WORD);
        assertThat(state.cursorCol()).isEqualTo(7);

        Buffer buffer = Buffer.empty(new Rect(0, 0, 7, 3));
        Frame frame = Frame.forTesting(buffer);

        textArea.renderWithCursor(buffer.area(), buffer, state, frame);

        assertThat(buffer.get(0, 1).style()).isEqualTo(cursorStyle);
    }

    @Test
    @DisplayName("render falls back to clipping for truncating overflow modes")
    void renderTreatsEllipsisAsClip() {
        for (Overflow truncating : new Overflow[] {
            Overflow.ELLIPSIS, Overflow.ELLIPSIS_START, Overflow.ELLIPSIS_MIDDLE}) {
            TextArea textArea = TextArea.builder().overflow(truncating).build();
            TextAreaState state = new TextAreaState("one two three");
            state.moveCursorToStart(); // avoid CLIP auto-scrolling to the end-of-text cursor
            Buffer buffer = Buffer.empty(new Rect(0, 0, 7, 3));

            textArea.render(buffer.area(), buffer, state);

            assertThat(extractLineText(buffer, 0)).as("%s row 0", truncating).isEqualTo("one two");
            // Not wrapped: "three" must not appear on row 1, and nothing is truncated with "...".
            assertThat(extractLineText(buffer, 1)).as("%s row 1", truncating).isEmpty();
        }
    }

    @Test
    @DisplayName("render publishes the content width the rows were actually wrapped to")
    void renderPublishesContentWidth() {
        TextArea textArea = TextArea.builder()
            .overflow(Overflow.WRAP_WORD)
            .showLineNumbers(true)
            .block(Block.builder().borders(Borders.ALL).build())
            .build();
        TextAreaState state = new TextAreaState("one two three four five");
        Buffer buffer = Buffer.empty(new Rect(0, 0, 20, 5));

        textArea.render(buffer.area(), buffer, state);

        // 20 - 2 (border) - 4 (gutter: max(2, 1 digit) + 2) = 14
        assertThat(state.lastRenderedWidth()).isEqualTo(14);

        // Guards against the published width drifting from the widget's own layout math:
        // re-wrapping at lastRenderedWidth must reproduce exactly what was drawn.
        List<TextAreaState.DisplayRow> rows =
            state.computeDisplayRows(state.lastRenderedWidth(), Overflow.WRAP_WORD);
        assertThat(rows).hasSize(2);
        int textLeft = 1 + 4; // border + gutter
        for (int i = 0; i < rows.size(); i++) {
            TextAreaState.DisplayRow row = rows.get(i);
            String expected = state.getLine(row.logicalRow()).substring(row.startCol(), row.endCol());
            assertThat(extractRegion(buffer, 1 + i, textLeft, state.lastRenderedWidth()))
                .as("display row %d", i)
                .isEqualTo(expected);
        }
    }

    /** The rendered text of {@code width} columns starting at {@code fromX}, right-trimmed. */
    private String extractRegion(Buffer buffer, int y, int fromX, int width) {
        StringBuilder sb = new StringBuilder();
        for (int x = fromX; x < fromX + width; x++) {
            if (!buffer.get(x, y).isContinuation()) {
                sb.append(buffer.get(x, y).symbol());
            }
        }
        return sb.toString().replaceAll("\\s+$", "");
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
