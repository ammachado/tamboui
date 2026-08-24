/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.widgets.input;

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.style.Overflow;
import dev.tamboui.text.CharWidth;

/**
 * State for a TextArea widget, tracking multi-line text, cursor position, and scroll offset.
 */
public final class TextAreaState {

    private final List<StringBuilder> lines;
    private int cursorRow;
    private int cursorCol;
    private int scrollRow;
    private int scrollCol;

    /** Creates a new empty text area state. */
    public TextAreaState() {
        this.lines = new ArrayList<>();
        this.lines.add(new StringBuilder());
        this.cursorRow = 0;
        this.cursorCol = 0;
        this.scrollRow = 0;
        this.scrollCol = 0;
    }

    /**
     * Creates a new text area state with the given initial text.
     *
     * @param initialText the initial text content
     */
    public TextAreaState(String initialText) {
        this();
        setText(initialText);
    }

    // --- Text Access ---

    /**
     * Returns the full text content.
     *
     * @return the text
     */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * Returns the number of lines.
     *
     * @return the line count
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * Returns the text of the line at the given row.
     *
     * @param row the row index
     * @return the line text, or empty string if out of range
     */
    public String getLine(int row) {
        if (row >= 0 && row < lines.size()) {
            return lines.get(row).toString();
        }
        return "";
    }

    // --- Cursor Access ---

    /**
     * Returns the cursor row.
     *
     * @return the cursor row index
     */
    public int cursorRow() {
        return cursorRow;
    }

    /**
     * Returns the cursor column.
     *
     * @return the cursor column index
     */
    public int cursorCol() {
        return cursorCol;
    }

    /**
     * Returns the vertical scroll offset.
     *
     * @return the scroll row
     */
    public int scrollRow() {
        return scrollRow;
    }

    /**
     * Returns the horizontal scroll offset.
     *
     * @return the scroll column
     */
    public int scrollCol() {
        return scrollCol;
    }

    // --- Text Modification ---

    /**
     * Inserts a character at the cursor position.
     *
     * @param c the character to insert
     */
    public void insert(char c) {
        if (c == '\n') {
            insertNewline();
        } else {
            lines.get(cursorRow).insert(cursorCol, c);
            cursorCol++;
        }
    }

    /**
     * Inserts a string at the cursor position.
     *
     * @param s the string to insert
     */
    public void insert(String s) {
        for (char c : s.toCharArray()) {
            insert(c);
        }
    }

    private void insertNewline() {
        StringBuilder currentLine = lines.get(cursorRow);
        String afterCursor = currentLine.substring(cursorCol);
        currentLine.setLength(cursorCol);
        cursorRow++;
        cursorCol = 0;
        lines.add(cursorRow, new StringBuilder(afterCursor));
    }

    /** Deletes the grapheme cluster before the cursor. */
    public void deleteBackward() {
        if (cursorCol > 0) {
            StringBuilder line = lines.get(cursorRow);
            int start = GraphemeClusters.clusterStart(line, cursorCol);
            line.delete(start, cursorCol);
            cursorCol = start;
        } else if (cursorRow > 0) {
            // Merge with previous line
            StringBuilder prevLine = lines.get(cursorRow - 1);
            cursorCol = prevLine.length();
            prevLine.append(lines.get(cursorRow));
            lines.remove(cursorRow);
            cursorRow--;
        }
    }

    /** Deletes the grapheme cluster after the cursor. */
    public void deleteForward() {
        StringBuilder currentLine = lines.get(cursorRow);
        if (cursorCol < currentLine.length()) {
            int end = GraphemeClusters.clusterEnd(currentLine, cursorCol);
            currentLine.delete(cursorCol, end);
        } else if (cursorRow < lines.size() - 1) {
            // Merge with next line
            currentLine.append(lines.get(cursorRow + 1));
            lines.remove(cursorRow + 1);
        }
    }

    // --- Cursor Movement ---

    /** Moves the cursor one grapheme cluster to the left. */
    public void moveCursorLeft() {
        if (cursorCol > 0) {
            cursorCol = GraphemeClusters.clusterStart(lines.get(cursorRow), cursorCol);
        } else if (cursorRow > 0) {
            cursorRow--;
            cursorCol = lines.get(cursorRow).length();
        }
    }

    /** Moves the cursor one grapheme cluster to the right. */
    public void moveCursorRight() {
        StringBuilder currentLine = lines.get(cursorRow);
        if (cursorCol < currentLine.length()) {
            cursorCol = GraphemeClusters.clusterEnd(currentLine, cursorCol);
        } else if (cursorRow < lines.size() - 1) {
            cursorRow++;
            cursorCol = 0;
        }
    }

    /** Moves the cursor one logical row up (assumes {@link Overflow#CLIP}, no wrapping). */
    public void moveCursorUp() {
        moveCursorUpClip();
    }

    /**
     * Moves the cursor up one row.
     * <p>
     * With {@link Overflow#CLIP}, behaves exactly like {@link #moveCursorUp()} (one logical
     * row). With {@code WRAP_WORD}/{@code WRAP_CHARACTER}, moves to the previous visual
     * (wrapped) row, which may be a wrapped segment of the same logical line or the last
     * segment of the previous logical line.
     *
     * @param visibleCols the number of visible columns (used to compute wrapped rows)
     * @param wrap        the wrap mode
     */
    public void moveCursorUp(int visibleCols, Overflow wrap) {
        if (wrap == null || wrap == Overflow.CLIP) {
            moveCursorUpClip();
            return;
        }

        List<DisplayRow> rows = computeDisplayRows(visibleCols, wrap);
        int index = findDisplayRowIndex(rows, cursorRow, cursorCol);
        if (index <= 0) {
            return;
        }
        moveCursorToDisplayRow(rows.get(index - 1));
    }

    private void moveCursorUpClip() {
        if (cursorRow > 0) {
            cursorRow--;
            cursorCol = Math.min(cursorCol, lines.get(cursorRow).length());
        }
    }

    /** Moves the cursor one logical row down (assumes {@link Overflow#CLIP}, no wrapping). */
    public void moveCursorDown() {
        moveCursorDownClip();
    }

    /**
     * Moves the cursor down one row.
     * <p>
     * With {@link Overflow#CLIP}, behaves exactly like {@link #moveCursorDown()} (one logical
     * row). With {@code WRAP_WORD}/{@code WRAP_CHARACTER}, moves to the next visual (wrapped)
     * row, which may be a wrapped segment of the same logical line or the first segment of the
     * next logical line.
     *
     * @param visibleCols the number of visible columns (used to compute wrapped rows)
     * @param wrap        the wrap mode
     */
    public void moveCursorDown(int visibleCols, Overflow wrap) {
        if (wrap == null || wrap == Overflow.CLIP) {
            moveCursorDownClip();
            return;
        }

        List<DisplayRow> rows = computeDisplayRows(visibleCols, wrap);
        int index = findDisplayRowIndex(rows, cursorRow, cursorCol);
        if (index < 0 || index >= rows.size() - 1) {
            return;
        }
        moveCursorToDisplayRow(rows.get(index + 1));
    }

    private void moveCursorDownClip() {
        if (cursorRow < lines.size() - 1) {
            cursorRow++;
            cursorCol = Math.min(cursorCol, lines.get(cursorRow).length());
        }
    }

    private void moveCursorToDisplayRow(DisplayRow target) {
        cursorRow = target.logicalRow();
        cursorCol = Math.max(target.startCol(), Math.min(cursorCol, target.endCol()));
    }

    /** Moves the cursor to the start of the current line. */
    public void moveCursorToLineStart() {
        cursorCol = 0;
    }

    /** Moves the cursor to the end of the current line. */
    public void moveCursorToLineEnd() {
        cursorCol = lines.get(cursorRow).length();
    }

    /** Moves the cursor to the very beginning of the text. */
    public void moveCursorToStart() {
        cursorRow = 0;
        cursorCol = 0;
    }

    /** Moves the cursor to the very end of the text. */
    public void moveCursorToEnd() {
        cursorRow = lines.size() - 1;
        cursorCol = lines.get(cursorRow).length();
    }

    // --- Wrapping ---

    /**
     * One visually-wrapped row of a logical line: the character-offset range
     * {@code [startCol, endCol)} of that line rendered on a single screen row.
     */
    public static final class DisplayRow {
        private final int logicalRow;
        private final int startCol;
        private final int endCol;

        DisplayRow(int logicalRow, int startCol, int endCol) {
            this.logicalRow = logicalRow;
            this.startCol = startCol;
            this.endCol = endCol;
        }

        /** @return the logical (newline-delimited) line index this row belongs to */
        public int logicalRow() {
            return logicalRow;
        }

        /** @return the char offset (inclusive) where this row starts within its logical line */
        public int startCol() {
            return startCol;
        }

        /** @return the char offset (exclusive) where this row ends within its logical line */
        public int endCol() {
            return endCol;
        }
    }

    /**
     * Computes the visual rows produced by wrapping every logical line to {@code visibleWidth}.
     * <p>
     * With {@link Overflow#CLIP} (or a non-positive width) each logical line maps to exactly
     * one display row, matching the unwrapped behavior.
     *
     * @param visibleWidth the width available for text, in display columns
     * @param wrap         the wrap mode
     * @return the display rows, in document order
     */
    public List<DisplayRow> computeDisplayRows(int visibleWidth, Overflow wrap) {
        List<DisplayRow> result = new ArrayList<>();
        boolean noWrap = wrap == null || wrap == Overflow.CLIP || visibleWidth <= 0;
        for (int row = 0; row < lines.size(); row++) {
            String line = lines.get(row).toString();
            if (noWrap) {
                result.add(new DisplayRow(row, 0, line.length()));
                continue;
            }
            if (line.isEmpty()) {
                result.add(new DisplayRow(row, 0, 0));
                continue;
            }
            List<int[]> segments = wrap == Overflow.WRAP_WORD
                ? wrapLineByWord(line, visibleWidth)
                : wrapLineByCharacter(line, visibleWidth);
            for (int[] segment : segments) {
                result.add(new DisplayRow(row, segment[0], segment[1]));
            }
        }
        return result;
    }

    private static List<int[]> wrapLineByCharacter(String line, int maxWidth) {
        List<int[]> segments = new ArrayList<>();
        int start = 0;
        int width = 0;
        int i = 0;
        int len = line.length();
        while (i < len) {
            int codePoint = line.codePointAt(i);
            int codePointWidth = CharWidth.of(codePoint);
            int charCount = Character.charCount(codePoint);

            if (width + codePointWidth > maxWidth) {
                if (width == 0) {
                    // Single character wider than maxWidth: give it its own row.
                    segments.add(new int[] {start, i + charCount});
                    i += charCount;
                    start = i;
                    width = 0;
                    continue;
                }
                segments.add(new int[] {start, i});
                start = i;
                width = 0;
                continue;
            }

            width += codePointWidth;
            i += charCount;
        }
        segments.add(new int[] {start, len});
        return segments;
    }

    private static List<int[]> wrapLineByWord(String line, int maxWidth) {
        List<Integer> cpOffsets = new ArrayList<>();
        List<Integer> cpWidths = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            int codePoint = line.codePointAt(i);
            cpOffsets.add(i);
            cpWidths.add(CharWidth.of(codePoint));
            i += Character.charCount(codePoint);
        }
        cpOffsets.add(line.length());
        int cpCount = cpWidths.size();

        List<int[]> segments = new ArrayList<>();
        int pos = 0;
        while (pos < cpCount) {
            int lineEnd = findNextWordBreakByWidth(line, cpOffsets, cpWidths, pos, cpCount, maxWidth);
            segments.add(new int[] {cpOffsets.get(pos), cpOffsets.get(lineEnd)});

            // Skip whitespace consumed by the break so the next row never starts with it.
            int nextPos = lineEnd;
            while (nextPos < cpCount && Character.isWhitespace(line.codePointAt(cpOffsets.get(nextPos)))) {
                nextPos++;
            }
            pos = nextPos;
        }
        return segments;
    }

    private static int findNextWordBreakByWidth(String text, List<Integer> cpOffsets, List<Integer> cpWidths,
                                                  int startPos, int cpCount, int maxWidth) {
        int width = 0;
        int maxEnd = startPos;
        while (maxEnd < cpCount) {
            int codePointWidth = cpWidths.get(maxEnd);
            if (width + codePointWidth > maxWidth) {
                break;
            }
            width += codePointWidth;
            maxEnd++;
        }

        if (maxEnd == startPos) {
            // Single code point wider than maxWidth: force progress.
            maxEnd = Math.min(startPos + 1, cpCount);
        }

        if (maxEnd >= cpCount) {
            return cpCount;
        }

        // The fit already ends exactly at a word boundary; no need to backtrack.
        if (Character.isWhitespace(text.codePointAt(cpOffsets.get(maxEnd)))) {
            return maxEnd;
        }

        for (int idx = maxEnd - 1; idx > startPos; idx--) {
            int codePoint = text.codePointAt(cpOffsets.get(idx));
            if (Character.isWhitespace(codePoint)) {
                return idx;
            }
        }

        for (int idx = maxEnd - 1; idx > startPos; idx--) {
            int codePoint = text.codePointAt(cpOffsets.get(idx));
            if (codePoint == '-' || codePoint == '/' || codePoint == '\\') {
                return idx + 1;
            }
        }

        return maxEnd;
    }

    // --- Scrolling ---

    /**
     * Adjusts scroll offsets to keep the cursor visible, assuming {@link Overflow#CLIP}
     * (no wrapping, horizontal scroll instead).
     *
     * @param visibleRows the number of visible rows
     * @param visibleCols the number of visible columns
     */
    public void ensureCursorVisible(int visibleRows, int visibleCols) {
        ensureCursorVisibleClip(visibleRows, visibleCols);
    }

    /**
     * Adjusts scroll offsets to keep the cursor visible.
     * <p>
     * With {@link Overflow#CLIP}, behaves exactly like {@link #ensureCursorVisible(int, int)}.
     * With {@code WRAP_WORD}/{@code WRAP_CHARACTER}, {@code scrollRow} is treated as an index
     * into the wrapped display rows (see {@link #computeDisplayRows}) and {@code scrollCol} is
     * always {@code 0}, since a wrapped row always fits within {@code visibleCols}.
     *
     * @param visibleRows the number of visible rows
     * @param visibleCols the number of visible columns
     * @param wrap        the wrap mode
     */
    public void ensureCursorVisible(int visibleRows, int visibleCols, Overflow wrap) {
        if (wrap == null || wrap == Overflow.CLIP) {
            ensureCursorVisibleClip(visibleRows, visibleCols);
            return;
        }

        List<DisplayRow> rows = computeDisplayRows(visibleCols, wrap);
        int cursorDisplayIndex = findDisplayRowIndex(rows, cursorRow, cursorCol);

        if (cursorDisplayIndex < scrollRow) {
            scrollRow = cursorDisplayIndex;
        } else if (cursorDisplayIndex >= scrollRow + visibleRows) {
            scrollRow = cursorDisplayIndex - visibleRows + 1;
        }
        scrollCol = 0;
    }

    private void ensureCursorVisibleClip(int visibleRows, int visibleCols) {
        // Vertical scrolling
        if (cursorRow < scrollRow) {
            scrollRow = cursorRow;
        } else if (cursorRow >= scrollRow + visibleRows) {
            scrollRow = cursorRow - visibleRows + 1;
        }

        // Horizontal scrolling (display-width aware: cursorCol is a char offset)
        String line = getLine(cursorRow);
        if (cursorCol <= scrollCol) {
            scrollCol = cursorCol;
        } else {
            int from = Math.min(scrollCol, line.length());
            int to = Math.min(cursorCol, line.length());
            int displayWidth = CharWidth.of(line.substring(from, to));
            if (displayWidth >= visibleCols) {
                scrollCol = findScrollColForCursor(line, cursorCol, visibleCols);
            }
        }
    }

    /**
     * Finds the index within {@code rows} (as returned by {@link #computeDisplayRows}) of the
     * display row containing the given logical position.
     *
     * @param rows       the display rows to search
     * @param logicalRow the logical (newline-delimited) line index
     * @param col        the char offset within that logical line
     * @return the index of the containing display row
     */
    public static int findDisplayRowIndex(List<DisplayRow> rows, int logicalRow, int col) {
        int best = 0;
        for (int i = 0; i < rows.size(); i++) {
            DisplayRow row = rows.get(i);
            if (row.logicalRow() == logicalRow && row.startCol() <= col) {
                best = i;
            } else if (row.logicalRow() > logicalRow) {
                break;
            }
        }
        return best;
    }

    private static int findScrollColForCursor(String line, int cursorCol, int visibleCols) {
        int end = Math.min(cursorCol, line.length());
        // prefixWidth[i] = display width of line.substring(0, i), respecting ZWJ
        // sequences and Regional Indicator pairs the same way CharWidth.of(String) does.
        // For a multi-char cluster, the width is recorded on the offset past the cluster;
        // intermediate offsets carry the running width as it stood before the cluster.
        int[] prefixWidth = new int[end + 1];
        int width = 0;
        int i = 0;
        while (i < end) {
            int cp = line.codePointAt(i);
            int charCount = Character.charCount(cp);
            int clusterChars;
            int clusterWidth;

            if (cp == 0x200D) {
                // ZWJ + following code point: contributes 0 (the joiner and the joined glyph).
                clusterChars = charCount;
                if (i + charCount < end) {
                    clusterChars += Character.charCount(line.codePointAt(i + charCount));
                }
                clusterWidth = 0;
            } else if (cp >= 0x1F1E6 && cp <= 0x1F1FF
                && i + charCount < end
                && isRegionalIndicator(line.codePointAt(i + charCount))) {
                int nextCp = line.codePointAt(i + charCount);
                clusterChars = charCount + Character.charCount(nextCp);
                clusterWidth = 2;
            } else {
                clusterChars = charCount;
                clusterWidth = CharWidth.of(cp);
            }

            int newWidth = width + clusterWidth;
            // Carry the previous running width across intermediate offsets, then jump
            // to the new running width at the offset past the cluster.
            for (int j = 1; j < clusterChars; j++) {
                prefixWidth[i + j] = width;
            }
            prefixWidth[i + clusterChars] = newWidth;
            width = newWidth;
            i += clusterChars;
        }

        int totalWidth = prefixWidth[end];
        for (int col = end - 1; col >= 0; col--) {
            if (totalWidth - prefixWidth[col] >= visibleCols) {
                return col + 1;
            }
        }
        return 0;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    /**
     * Scrolls up by the given amount of rows.
     *
     * @param amount the number of rows to scroll up
     */
    public void scrollUp(int amount) {
        scrollRow = Math.max(0, scrollRow - amount);
    }

    /**
     * Scrolls down by the given amount of rows.
     *
     * @param amount      the number of rows to scroll down
     * @param visibleRows the number of visible rows
     */
    public void scrollDown(int amount, int visibleRows) {
        int maxScroll = Math.max(0, lines.size() - visibleRows);
        scrollRow = Math.min(maxScroll, scrollRow + amount);
    }

    // --- Bulk Operations ---

    /** Clears all text and resets the cursor and scroll positions. */
    public void clear() {
        lines.clear();
        lines.add(new StringBuilder());
        cursorRow = 0;
        cursorCol = 0;
        scrollRow = 0;
        scrollCol = 0;
    }

    /**
     * Replaces the text content and moves the cursor to the end.
     *
     * @param newText the new text content
     */
    public void setText(String newText) {
        lines.clear();
        if (newText == null || newText.isEmpty()) {
            lines.add(new StringBuilder());
        } else {
            String[] splitLines = newText.split("\n", -1);
            for (String line : splitLines) {
                lines.add(new StringBuilder(line));
            }
        }
        cursorRow = lines.size() - 1;
        cursorCol = lines.get(cursorRow).length();
        scrollRow = 0;
        scrollCol = 0;
    }
}
